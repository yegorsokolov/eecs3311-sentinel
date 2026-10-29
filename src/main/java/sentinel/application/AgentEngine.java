package sentinel.application;

import java.util.*;
import sentinel.domain.*;
import sentinel.data.*;
import sentinel.planning.*;
import sentinel.tools.*;
import sentinel.analysis.*;
import sentinel.events.*;

/** Shared rule-planning/manual-query service; model planning and autonomous analysis follow separately. */
public class AgentEngine {
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
            Plan plan = new PlanValidator(tools, datasetStore()).validate(strategy.createPlan(context), context);
            investigation.setPlan(plan);
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

    public Assessment run(Investigation investigation) {
        throw new UnsupportedOperationException("Bounded autonomous orchestration is implemented in its milestone.");
    }

    public Answer ask(Investigation investigation, String question) {
        throw new UnsupportedOperationException("Follow-up analysis is implemented in its milestone.");
    }
}
