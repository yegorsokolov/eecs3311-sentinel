package sentinel.application;

import java.util.*;
import sentinel.domain.*;
import sentinel.data.*;
import sentinel.planning.*;
import sentinel.tools.*;
import sentinel.analysis.*;
import sentinel.events.*;
import sentinel.llm.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Shared validated planning, scoped retrieval and bounded investigation orchestration. */
public class AgentEngine {
    private final Map<UUID, ModelBudget> modelBudgets = new ConcurrentHashMap<>();
    private final Map<UUID, AtomicBoolean> cancellations = new ConcurrentHashMap<>();
    private final PlanningStrategy planner;
    private final ToolExecutor tools;
    private final AnalysisService analysis;
    private final EventBus events;
    private final DatasetStore store;
    private final Map<ToolExecutor.Session, Binding> sessions = Collections.synchronizedMap(new WeakHashMap<>());
    private record Binding(UUID caseId, ToolScope scope) { }

    /** Legacy scaffold construction; integrated workflows require the DatasetStore overload. */
    public AgentEngine(PlanningStrategy planner, ToolExecutor tools, AnalysisService analysis, EventBus events) {
        this(planner, tools, analysis, events, null);
    }

    public AgentEngine(PlanningStrategy planner, ToolExecutor tools, AnalysisService analysis, EventBus events, DatasetStore store) {
        this.planner = Objects.requireNonNull(planner);
        this.tools = Objects.requireNonNull(tools);
        this.analysis = Objects.requireNonNull(analysis);
        this.events = Objects.requireNonNull(events);
        this.store = store;
    }

    public DatasetStore datasetStore() {
        return Objects.requireNonNull(store, "Integrated engine requires the shared DatasetStore.");
    }

    /** Planning never runs tools; clarification/approval failures leave the same draft inspectable. */
    public Plan plan(Request request, Investigation investigation) {
        synchronized (investigation) {
            var snapshot = investigation.snapshot();
            if (!request.goal().equals(snapshot.goal()) || !Objects.equals(request.scope(), snapshot.scope())) {
                throw new PlanningException(PlanningException.Code.INVALID_CONTEXT, "Request differs from the active case.");
            }
            return preparePlan(investigation, planner, false);
        }
    }

    public Plan acceptRulePlan(Investigation investigation) {
        synchronized (investigation) {
            var validator = new PlanValidator(tools, datasetStore());
            return preparePlan(investigation, new RulePlanningStrategy(validator, datasetStore()), true);
        }
    }

    private Plan preparePlan(Investigation investigation, PlanningStrategy strategy, boolean approved) {
        if (investigation.snapshot().status() == CaseStatus.FAILED) {
            throw new PlanningException(PlanningException.Code.INVALID_CONTEXT, "Failed cases cannot be replanned.");
        }
        try {
            var context = new CaseContext(investigation.snapshot(), approved);
            if (context.snapshot().scope() == null) {
                throw new PlanningException(PlanningException.Code.CLARIFICATION_REQUIRED, "Provide complete case scope.");
            }
            Plan plan = new PlanValidator(tools, datasetStore()).validate(strategy.createPlan(context, modelBudgets.computeIfAbsent(context.snapshot().id(), id -> new ModelBudget())), context);
            investigation.setPlan(plan);
            if (plan.origin() == Plan.Origin.MODEL) investigation.recordModelUse(strategy.modelVersion(), strategy.promptVersion());
            events.publish(new ProgressEvent("PLANNING", plan.origin().name() + ":READY", List.of()));
            return plan;
        } catch (PlanningException ex) {
            investigation.recordAction("PLANNING_BLOCKED", ex.code().name(), List.of());
            events.publish(new ProgressEvent("PLANNING", ex.code().name(), List.of()));
            return null;
        }
    }

    /** One explicit manual query has its own budget; autonomous collections reuse beginQuerySession. */
    public ToolResult query(Investigation investigation, ToolCall call) {
        ToolExecutor.Session session;
        try { session = beginQuerySession(investigation); }
        catch (ToolException ex) {
            synchronized (investigation) {
                if (investigation.snapshot().status() != CaseStatus.FAILED) {
                    investigation.recordAction("TOOL_FAILED", ex.code().name(), List.of());
                }
            }
            events.publish(new ProgressEvent("RETRIEVAL", ex.code().name(), List.of()));
            throw ex;
        }
        try { return query(investigation, call, session); }
        finally { sessions.remove(session); }
    }

    public ToolExecutor.Session beginQuerySession(Investigation investigation) {
        synchronized (investigation) {
            var snapshot = investigation.snapshot();
            if (snapshot.scope() == null) throw new ToolException(ToolException.Code.SCOPE_REQUIRED, "Case scope requires clarification.");
            if (snapshot.status() == CaseStatus.FAILED) throw new ToolException(ToolException.Code.OUT_OF_SCOPE, "Failed cases cannot retrieve.");
            if (!datasetStore().datasetId().equals(snapshot.scope().datasetId())) {
                throw new ToolException(ToolException.Code.UNKNOWN_DATASET, "Case dataset is unavailable.");
            }
            try { datasetStore().events(snapshot.scope().datasetVersion()); }
            catch (IllegalArgumentException ex) { throw new ToolException(ToolException.Code.UNKNOWN_VERSION, "Case dataset version is unavailable."); }
            ToolScope scope = CaseEvidence.scope(datasetStore(), snapshot);
            var session = tools.openSession(scope);
            sessions.put(session, new Binding(snapshot.id(), scope));
            return session;
        }
    }

    public ToolResult query(Investigation investigation, ToolCall call, ToolExecutor.Session session) {
        synchronized (investigation) {
            try {
                var snapshot = investigation.snapshot();
                Binding binding = sessions.get(session);
                if (binding == null || !snapshot.id().equals(binding.caseId())
                        || !Objects.equals(snapshot.scope(), binding.scope().caseScope())
                        || !snapshot.targetType().name().equals(binding.scope().targetKind().name())
                        || !Objects.equals(snapshot.feedVersion(), binding.scope().feedVersion())
                        || snapshot.status() == CaseStatus.FAILED) {
                    throw new ToolException(ToolException.Code.OUT_OF_SCOPE, "Execution session no longer belongs to this case scope.");
                }
                ToolResult result = session.execute(call);
                ResultVerifier.validate(datasetStore(), snapshot, call, result);
                investigation.addEvidence(result);
                // Only canonical, accepted, currently in-scope events authorize enrichment. No budget reset.
                try { session.acceptEvidenceIndicators(CaseEvidence.indicators(datasetStore(), investigation.snapshot())); }
                catch (ToolException ex) {
                    // A completed source-validated result is already committed. Closing only prevents later reuse.
                    if (ex.code() != ToolException.Code.EXECUTOR_CLOSED) throw ex;
                }
                investigation.recordAction("TOOL_SUCCEEDED", call.toolName() + ":" + result.status(),
                        result.evidence().stream().map(Evidence::id).distinct().toList());
                events.publish(new ProgressEvent("RETRIEVAL", result.status(),
                        result.evidence().stream().map(Evidence::id).distinct().toList()));
                return result;
            } catch (ToolException ex) {
                if (investigation.snapshot().status() != CaseStatus.FAILED) {
                    investigation.recordAction("TOOL_FAILED", ex.code().name(), List.of());
                }
                events.publish(new ProgressEvent("RETRIEVAL", ex.code().name(), List.of()));
                throw ex;
            }
        }
    }

    public void invalidateSessions(UUID caseId) {
        synchronized (sessions) { sessions.entrySet().removeIf(entry -> entry.getValue().caseId().equals(caseId)); }
    }


    public Plan revalidate(Investigation investigation) {
        synchronized(investigation) {
            var s=investigation.snapshot();if(s.status()!=CaseStatus.PAUSED)throw new IllegalStateException("Revalidation requires a paused saved case.");
            var validated=new PlanValidator(tools,datasetStore()).validate(s.plan(),new CaseContext(s,s.plan().origin()==Plan.Origin.RULE));
            investigation.setPlan(validated);investigation.recordAction("PLAN_REVALIDATED","READY",List.of());return validated;
        }
    }
    public void cancel(UUID id) { var flag=cancellations.get(id);if(flag!=null)flag.set(true); }
    public void inventory(Set<String> hosts) { analysis.inventory(hosts); }
    public Assessment run(Investigation investigation) {
        UUID id=investigation.snapshot().id();var flag=new AtomicBoolean();boolean started=false;
        if(cancellations.putIfAbsent(id,flag)!=null)throw new IllegalStateException("Case already running.");
        try {
            var snapshot=investigation.snapshot();if(snapshot.status()!=CaseStatus.READY)throw new IllegalStateException("Run requires a validated READY plan; paused cases must be revalidated.");
            new PlanValidator(tools,datasetStore()).validate(snapshot.plan(),new CaseContext(snapshot,snapshot.plan().origin()==Plan.Origin.RULE));
            var session=beginQuerySession(investigation);var done=new HashSet<UUID>();boolean partial=false;
            investigation.recordAction("STATE_CHANGED","RUNNING",List.of());started=true;
            events.publish(new ProgressEvent("RUN","RUNNING",List.of()));
            while(true){
                if(flag.get()||Thread.currentThread().isInterrupted()){flag.set(true);partial=true;investigation.recordAction("RUN_STOPPED","CANCELLED",List.of());break;}
                var current=investigation.snapshot().plan();var step=current.next(done);
                if(step.isEmpty()){
                    // Observation-driven read-only enrichment: revise the visible plan, preserving session accounting.
                    var additions=new ArrayList<ToolCall>();var keys=current.steps().stream().map(ToolCall::key).collect(java.util.stream.Collectors.toSet());
                    var s=investigation.snapshot();if(s.feedVersion()!=null && current.revision()<2)for(String indicator:CaseEvidence.indicators(datasetStore(),s).stream().sorted().toList()){
                        var call=new ToolCall(UUID.randomUUID(),"indicatorLookup",Map.of("indicator",indicator,"feedVersion",s.feedVersion()));
                        if(!keys.contains(call.key()) && current.steps().size()+additions.size()<8 && additions.size()<session.attemptsRemaining())additions.add(call);
                    }
                    if(additions.isEmpty())break;
                    var all=new ArrayList<>(current.steps());all.addAll(additions);var revised=new Plan(UUID.randomUUID(),current.revision()+1,all,current.dependencies(),current.origin());
                    new PlanValidator(tools,datasetStore()).validate(revised,new CaseContext(s,current.origin()==Plan.Origin.RULE));investigation.setPlan(revised);investigation.recordAction("PLAN_REVISED","OBSERVED_INDICATORS",List.of());continue;
                }
                try{ToolResult result=query(investigation,step.get(),session);partial|=result.truncated()||result.status().equals("STALE");done.add(step.get().id());}
                catch(ToolException ex){if(ex.code()==ToolException.Code.INTERRUPTED)flag.set(true);partial=true;investigation.recordAction("RUN_STOPPED",ex.code().name(),List.of());break;}
            }
            if(Thread.currentThread().isInterrupted())flag.set(true);
            var budget=modelBudgets.computeIfAbsent(id,k->new ModelBudget());
            Assessment accepted=flag.get()?analysis.partial(investigation,"Run cancelled; AI analysis not requested"):analysis.analyze(investigation,budget,partial);
            if(flag.get()||Thread.currentThread().isInterrupted())accepted=analysis.partial(investigation,"Run cancelled; deterministic results retained");
            investigation.setAssessment(accepted);
            investigation.recordAction("STATE_CHANGED",accepted.completeness()==Completeness.COMPLETE?"COMPLETED":"PARTIAL",List.of());
            events.publish(new ProgressEvent("RUN",investigation.snapshot().status().name(),List.of()));
            return accepted;
        }catch(RuntimeException ex){
            if(investigation.snapshot().status()==CaseStatus.RUNNING){investigation.recordAction("STATE_CHANGED","FAILED",List.of());events.publish(new ProgressEvent("RUN","FAILED",List.of()));}
            throw ex;
        }finally{cancellations.remove(id);if(started){modelBudgets.remove(id);invalidateSessions(id);}}
    }
    public Answer ask(Investigation investigation,String question) {
        throw new UnsupportedOperationException("Follow-up analysis is implemented in its milestone.");
    }
}
