package sentinel.application;

import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import sentinel.analysis.*;
import sentinel.data.*;
import sentinel.domain.*;
import sentinel.events.*;
import sentinel.llm.*;
import sentinel.persistence.*;
import sentinel.planning.*;
import sentinel.reporting.*;
import sentinel.tools.*;
import static org.junit.jupiter.api.Assertions.*;

class BoundedOrchestrationTest {
    private final Instant time=Instant.parse("2026-10-22T12:00:00Z");
    private DatasetStore store;private ToolExecutor tools;private AgentEngine engine;private AtomicInteger modelCalls;private EventBus bus;private boolean offline;
    @BeforeEach void setup() {
        store=new DatasetStore("demo",Clock.fixed(time.plusSeconds(7*86400),ZoneOffset.UTC),Duration.ofDays(30));var rows=new ArrayList<Event>();
        for(int n=0;n<5;n++)rows.add(new Event("f"+n,time.plusSeconds(n),"login","alice","192.0.2.10","host","failure","fixture#/"+n,"pending"));
        rows.add(new Event("success",time.plusSeconds(5),"login","alice","192.0.2.10","host","success","fixture#/5","pending"));
        store.ingest(new ImportBatch(rows));store.loadIndicatorFeed(Path.of("data/indicators/threat-feed.json"));
        tools=ToolExecutor.local(store);modelCalls=new AtomicInteger();bus=new EventBus();engine=createEngine(null);
    }
    @AfterEach void close(){tools.close();Thread.interrupted();}
    private String reply() {return JsonCodec.encode(new AnalysisService.ShortSynthesis("analysis-v1",new AnalysisService.ShortHypothesis("Possible takeover","E1",List.of(),"LOW","Not causal proof"),new AnalysisService.ShortRecommendation("REVIEW","Review logins","E1","HIGH","Analyst approval")));}
    private AnalysisService analysis(){return new AnalysisService(p->{modelCalls.incrementAndGet();if(offline)throw new ModelException(ModelException.Code.UNAVAILABLE,"offline");return reply();},new TimelineBuilder(),new RuleSeverityPolicy(),store);}
    private AgentEngine createEngine(PlanningStrategy planner){return new AgentEngine(planner==null?ctx->null:planner,tools,analysis(),bus,store);}
    private Investigation draft(){return new Investigation(new Request("Investigate alice",new CaseScope("demo",store.currentVersion(),"alice",time,time.plusSeconds(3600))),TargetType.ACCOUNT,"demo-feed-v1");}
    private Investigation ready(){var i=draft();engine.acceptRulePlan(i);return i;}
    private ToolCall search(int limit){return new ToolCall(UUID.randomUUID(),"logSearch",Map.of("datasetId","demo","datasetVersion",store.currentVersion(),"accountId","alice","start",time.toString(),"end",time.plusSeconds(3600).toString(),"limit",String.valueOf(limit)));}
    private void replaceTools(Map<String,ToolCommand> registry){tools.close();tools=new ToolExecutor(registry);engine=createEngine(null);}
    @Test void runCollectsEnrichesRevisesAndCompletesWithFixedSeverity() {
        var progress=new ArrayList<String>();bus.subscribe(e->progress.add(e.stage()+":"+e.outcome()));var i=ready();var result=engine.run(i);
        assertEquals(CaseStatus.COMPLETED,i.snapshot().status());assertEquals(Completeness.COMPLETE,result.completeness());assertEquals(7,result.severity().score());
        assertEquals(6,result.timeline().size());assertEquals(7,i.snapshot().evidence().size());assertEquals(1,modelCalls.get());assertEquals(1,i.snapshot().plan().revision());
        assertTrue(i.snapshot().history().stream().anyMatch(a->a.action().equals("PLAN_REVISED")));assertTrue(progress.contains("RUN:RUNNING"));assertTrue(progress.contains("RUN:COMPLETED"));
    }
    @Test void failedToolPreservesAlreadyAcceptedEvidenceAndReturnsPartial() {
        replaceTools(Map.of("logSearch",new LogSearchCommand(store),"userActivity",c->{throw new ToolException(ToolException.Code.EXECUTION_FAILED,"fixture");},"indicatorLookup",new IndicatorLookupCommand(store)));
        var i=ready();var result=engine.run(i);assertEquals(CaseStatus.PARTIAL,i.snapshot().status());assertEquals(6,result.timeline().size());assertEquals(6,i.snapshot().evidence().size());
        assertTrue(i.snapshot().history().stream().anyMatch(a->a.action().equals("RUN_STOPPED")&&a.outcome().equals("EXECUTION_FAILED")));
    }
    @Test void truncatedCollectionCannotBecomeComplete() {
        var i=draft();i.setPlan(new Plan(UUID.randomUUID(),0,List.of(search(1)),Map.of(),Plan.Origin.RULE));
        var result=engine.run(i);assertEquals(CaseStatus.PARTIAL,i.snapshot().status());assertEquals(Completeness.PARTIAL,result.completeness());assertEquals(1,result.timeline().size());
    }
    @Test void toolRetriesUseSameEightAttemptBudget() {
        var attempts=new AtomicInteger();var query=new LogSearchCommand(store);
        replaceTools(Map.of("logSearch",c->{if(attempts.getAndIncrement()==0)throw new ToolException(ToolException.Code.TRANSIENT_FAILURE,"retry");return query.execute(c);},"indicatorLookup",new IndicatorLookupCommand(store)));
        var i=draft();var calls=new ArrayList<ToolCall>();for(int n=6;n<14;n++)calls.add(search(n));i.setPlan(new Plan(UUID.randomUUID(),0,calls,Map.of(),Plan.Origin.RULE));
        assertEquals(Completeness.PARTIAL,engine.run(i).completeness());assertEquals(8,attempts.get());
        assertTrue(i.snapshot().history().stream().anyMatch(a->a.action().equals("RUN_STOPPED")&&a.outcome().equals("BUDGET_EXHAUSTED")));
    }
    @Test void planningAndAnalysisShareSixCallBudget() {
        PlanningStrategy planner=new PlanningStrategy(){public Plan createPlan(CaseContext c){throw new AssertionError();}public Plan createPlan(CaseContext c,ModelBudget b){for(int n=0;n<6;n++)b.acquire();var proposed=new RulePlanningStrategy(new PlanValidator(tools,store),store).createPlan(new CaseContext(c.snapshot(),true));return new Plan(proposed.id(),proposed.revision(),proposed.steps(),proposed.dependencies(),Plan.Origin.MODEL);}};
        engine=createEngine(planner);var i=draft();engine.plan(new Request(i.snapshot().goal(),i.snapshot().scope()),i);
        assertEquals(Completeness.PARTIAL,engine.run(i).completeness());assertEquals(0,modelCalls.get());assertTrue(i.snapshot().history().stream().anyMatch(a->a.outcome().equals("BUDGET_EXHAUSTED")));
    }
    @Test void cancellationAfterToolKeepsEvidenceAndSkipsModel() {
        var cancelId=new UUID[]{null};var query=new LogSearchCommand(store);
        replaceTools(Map.of("logSearch",c->{engine.cancel(cancelId[0]);return query.execute(c);},"userActivity",new UserActivityCommand(store),"indicatorLookup",new IndicatorLookupCommand(store)));
        var i=ready();cancelId[0]=i.snapshot().id();var result=engine.run(i);assertEquals(Completeness.PARTIAL,result.completeness());assertEquals(6,result.timeline().size());assertEquals(0,modelCalls.get());
    }
    @Test void interruptionBeforeCollectionSkipsModelAndPreservesCallerFlag() {
        var i=ready();bus.subscribe(e->{if(e.stage().equals("RUN")&&e.outcome().equals("RUNNING"))Thread.currentThread().interrupt();});
        var result=engine.run(i);assertTrue(Thread.currentThread().isInterrupted());assertEquals(0,modelCalls.get());assertEquals(Completeness.PARTIAL,result.completeness());
    }
    @Test void cancellationDuringModelFinishesCurrentCallButRemainsPartial() {
        var selected=new Investigation[]{null};var service=new AnalysisService(p->{modelCalls.incrementAndGet();engine.cancel(selected[0].snapshot().id());return reply();},new TimelineBuilder(),new RuleSeverityPolicy(),store);
        engine=new AgentEngine(c->null,tools,service,bus,store);var i=ready();selected[0]=i;
        assertEquals(Completeness.PARTIAL,engine.run(i).completeness());assertEquals(CaseStatus.PARTIAL,i.snapshot().status());assertEquals(1,modelCalls.get());assertFalse(i.snapshot().evidence().isEmpty());
    }
    @Test void rejectingPausedRunDoesNotResetEarlierPlanningBudget() {
        PlanningStrategy planner=new PlanningStrategy(){public Plan createPlan(CaseContext c){throw new AssertionError();}public Plan createPlan(CaseContext c,ModelBudget b){for(int n=0;n<6;n++)b.acquire();var proposed=new RulePlanningStrategy(new PlanValidator(tools,store),store).createPlan(new CaseContext(c.snapshot(),true));return new Plan(proposed.id(),proposed.revision(),proposed.steps(),proposed.dependencies(),Plan.Origin.MODEL);}};
        engine=createEngine(planner);var i=draft();engine.plan(new Request(i.snapshot().goal(),i.snapshot().scope()),i);i.recordAction("STATE_CHANGED","RUNNING",List.of());
        var paused=Investigation.restore(i.snapshot());assertThrows(IllegalStateException.class,()->engine.run(paused));engine.acceptRulePlan(paused);
        assertEquals(Completeness.PARTIAL,engine.run(paused).completeness());assertEquals(0,modelCalls.get());
    }
    @Test void revisionCeilingIsPreservedDuringObservedEnrichment() {
        var i=ready();engine.acceptRulePlan(i);engine.acceptRulePlan(i);assertEquals(2,i.snapshot().plan().revision());
        engine.run(i);assertEquals(2,i.snapshot().plan().revision());assertEquals(6,i.snapshot().evidence().size());
    }
    @Test void offlineModelRetainsDeterministicAssessmentAndTerminalPartialState() {
        offline=true;var i=ready();var result=engine.run(i);assertEquals(CaseStatus.PARTIAL,i.snapshot().status());assertEquals(7,result.severity().score());assertEquals(6,result.timeline().size());assertTrue(result.hypotheses().isEmpty());
    }
    @Test void fatalAnalysisFailurePreservesEvidenceAndMarksFailed() {
        var service=new AnalysisService(p->{throw new IllegalStateException("internal fixture");},new TimelineBuilder(),new RuleSeverityPolicy(),store);engine=new AgentEngine(c->null,tools,service,bus,store);
        var i=ready();assertThrows(IllegalStateException.class,()->engine.run(i));assertEquals(CaseStatus.FAILED,i.snapshot().status());assertFalse(i.snapshot().evidence().isEmpty());
    }
    @Test void duplicateConcurrentRunIsRejectedAndControllerCancellationIsAvailable() throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var query=new LogSearchCommand(store);
        replaceTools(Map.of("logSearch",c->{entered.countDown();try{if(!release.await(3,TimeUnit.SECONDS))throw new IllegalStateException("test release timeout");}catch(InterruptedException x){Thread.currentThread().interrupt();throw new IllegalStateException(x);}return query.execute(c);},"userActivity",new UserActivityCommand(store),"indicatorLookup",new IndicatorLookupCommand(store)));
        var controller=new AgentController(engine,new JsonCaseRepository(Path.of("runtime/cases")),new LogImporter(store),new ReportService(),bus);
        var i=controller.start(new Request("Investigate alice",draft().snapshot().scope()),TargetType.ACCOUNT,"demo-feed-v1");controller.acceptRulePlan(i.snapshot().id());
        var caseId=i.snapshot().id();var worker=Executors.newSingleThreadExecutor();var cancelWorker=Executors.newSingleThreadExecutor();
        try{var running=worker.submit(()->controller.run(caseId));assertTrue(entered.await(2,TimeUnit.SECONDS));
            cancelWorker.submit(()->controller.cancel(caseId)).get(1,TimeUnit.SECONDS);release.countDown();assertEquals(Completeness.PARTIAL,running.get(3,TimeUnit.SECONDS).completeness());assertEquals(0,modelCalls.get());
        }finally{release.countDown();worker.shutdownNow();cancelWorker.shutdownNow();}
    }
    @Test void duplicateRunIsRejectedBeforeAnotherSessionStarts() {
        var i=ready();var checked=new java.util.concurrent.atomic.AtomicBoolean();
        bus.subscribe(e->{if(e.stage().equals("RUN")&&e.outcome().equals("RUNNING")){assertThrows(IllegalStateException.class,()->engine.run(i));checked.set(true);}});
        engine.run(i);assertTrue(checked.get());assertEquals(1,modelCalls.get());
    }
    @Test void draftWithoutValidatedPlanCannotRunOrSpendModelCalls() {
        var i=draft();assertThrows(IllegalStateException.class,()->engine.run(i));assertEquals(CaseStatus.DRAFT,i.snapshot().status());assertEquals(0,modelCalls.get());
    }
}
