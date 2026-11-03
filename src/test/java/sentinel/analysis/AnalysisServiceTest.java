package sentinel.analysis;

import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import sentinel.data.DatasetStore;
import sentinel.domain.*;
import sentinel.llm.*;
import sentinel.persistence.JsonCodec;
import static org.junit.jupiter.api.Assertions.*;

class AnalysisServiceTest {
    private final Instant time=Instant.parse("2026-10-22T12:00:00Z");
    private DatasetStore store;private Investigation investigation;private AtomicInteger calls;private List<Prompt> prompts;
    @BeforeEach void setup() {
        store=new DatasetStore("demo");var rows=new ArrayList<Event>();
        for(int n=0;n<5;n++)rows.add(event("f"+n,n,"alice","failure"));rows.add(event("success",5,"alice","success"));
        store.ingest(new ImportBatch(rows));investigation=accepted();calls=new AtomicInteger();prompts=new ArrayList<>();
    }
    private Event event(String id,long seconds,String account,String outcome) {
        return new Event(id,time.plusSeconds(seconds),"login",account,null,"host",outcome,"fixture#/"+id,"pending");
    }
    private Investigation accepted() {
        var scope=new CaseScope("demo",store.currentVersion(),"alice",time,time.plusSeconds(3600));var result=new Investigation(new Request("Investigate synthetic account",scope));
        result.addEvidence(store.search(new ToolCall(UUID.randomUUID(),"logSearch",Map.of("datasetId","demo","datasetVersion",scope.datasetVersion(),"accountId","alice","start",scope.start().toString(),"end",scope.end().toString(),"limit","1000"))));return result;
    }
    private String valid() {return compact("E1","REVIEW","MEDIUM");}
    private String compact(String ref,String action,String confidence) {
        return JsonCodec.encode(new AnalysisService.ShortSynthesis("analysis-v1",
                new AnalysisService.ShortHypothesis("Possible suspicious login sequence",ref,List.of(),confidence,"Correlation is not proof"),
                new AnalysisService.ShortRecommendation(action,"Review source authentication records",ref,"HIGH","Analyst approval")));
    }
    private AnalysisService service(String... replies) {
        return service(prompt->{int n=calls.getAndIncrement();prompts.add(prompt);assertTrue(n<replies.length,"Unbounded request");return replies[n];});
    }
    private AnalysisService service(LLMGateway gateway) {return new AnalysisService(gateway,new TimelineBuilder(),new RuleSeverityPolicy(),store);}
    private void deterministicPartial(Assessment result) {
        assertEquals(Completeness.PARTIAL,result.completeness());assertEquals(5,result.severity().score());assertEquals(6,result.timeline().size());
        assertTrue(result.hypotheses().isEmpty());assertTrue(result.recommendations().isEmpty());
    }
    @Test void compactSynthesisResolvesImmutableSourceCitationsAndKeepsSeverityFixed() {
        var result=service(valid()).analyze(investigation);assertEquals(Completeness.COMPLETE,result.completeness());
        assertEquals(5,result.severity().score());assertEquals(List.of("R1","R2"),result.severity().ruleIds());
        assertEquals(List.of(investigation.snapshot().evidence().get(0).id()),result.hypotheses().get(0).supportingEvidenceIds());
        assertTrue(result.recommendations().get(0).contains("analyst approval required"));assertEquals(1,calls.get());
        assertEquals("analysis-v1",investigation.snapshot().promptVersion());assertEquals("analysis",prompts.get(0).purpose());
        assertEquals(CaseStatus.DRAFT,investigation.snapshot().status());
    }
    @Test void fullSynthesisAcceptsSupportingAndContradictingCanonicalIds() {
        var evidence=investigation.snapshot().evidence();
        String reply=JsonCodec.encode(new AnalysisService.Synthesis("analysis-v1",List.of(new Hypothesis("Possible incident",List.of(evidence.get(0).id()),List.of(evidence.get(1).id()),"LOW",List.of("Alternative explanation"))),List.of(new AnalysisService.Recommendation("ESCALATE","Human review","HIGH",List.of(evidence.get(0).id()),List.of("Approval")))));
        var result=service(reply).analyze(investigation);assertEquals(Completeness.COMPLETE,result.completeness());
        assertEquals(List.of(evidence.get(1).id()),result.hypotheses().get(0).contradictingEvidenceIds());assertEquals(5,result.severity().score());
    }
    @Test void fabricatedCitationReceivesOneRepairAndCannotEscapeIntoAssessment() {
        var result=service(compact("E99","REVIEW","LOW"),valid()).analyze(investigation);
        assertEquals(Completeness.COMPLETE,result.completeness());assertEquals(2,calls.get());
        assertTrue(prompts.get(1).content().contains("INVALID_SCHEMA_OR_CITATION"));assertFalse(prompts.get(1).content().contains("E99"));
    }
    @Test void persistentInvalidCitationPreservesDeterministicPartialAndAudit() {
        deterministicPartial(service(compact("E99","REVIEW","LOW"),compact("E99","REVIEW","LOW")).analyze(investigation));assertEquals(2,calls.get());
        assertTrue(investigation.snapshot().history().stream().anyMatch(a->a.action().equals("ANALYSIS_UNAVAILABLE")&&a.outcome().equals("INVALID_RESPONSE")));
    }
    @ParameterizedTest @ValueSource(strings={"[]","null","42","not-json","{}"})
    void malformedTopLevelResponseIsBoundedAndPreservesPartialResults(String reply) {
        deterministicPartial(service(reply,reply).analyze(investigation));assertEquals(2,calls.get());
    }
    @Test void nullRecommendationPriorityIsRejectedAndRepaired() {
        String bad=valid().replace("\"priority\":\"HIGH\"","\"priority\":null");
        var result=service(bad,valid()).analyze(investigation);assertEquals(Completeness.COMPLETE,result.completeness());assertEquals(2,calls.get());
    }
    @ParameterizedTest @ValueSource(strings={"CONTAIN","SHELL","DELETE"})
    void executableOrUnknownActionCannotBecomeRecommendation(String action) {
        deterministicPartial(service(compact("E1",action,"LOW"),compact("E1",action,"LOW")).analyze(investigation));assertEquals(2,calls.get());
    }
    @Test void unsupportedConfidenceAndModelSeverityFieldsAreRejected() {
        assertFalse(service(valid()).validateCitations(compact("E1","REVIEW","0.99"),investigation));
        String bad=valid().replace("\"version\":\"analysis-v1\"","\"severity\":0,\"version\":\"analysis-v1\"");
        deterministicPartial(service(bad,bad).analyze(investigation));
    }
    @Test void unavailableModelPreservesSourceTimelineAndFixedScoreWithoutInventedAnalysis() {
        var result=service(p->{calls.incrementAndGet();throw new ModelException(ModelException.Code.UNAVAILABLE,"private-provider-marker");}).analyze(investigation);
        deterministicPartial(result);assertEquals(1,calls.get());assertFalse(investigation.snapshot().history().toString().contains("private-provider-marker"));
    }
    @Test void oneTransientRetryAndOneRepairChargeSameBudget() {
        var budget=new ModelBudget();var result=service(p->{int n=calls.getAndIncrement();if(n==0)throw new ModelException(ModelException.Code.TRANSIENT_FAILURE,"retry");return n==1?"{}":valid();}).analyze(investigation,budget,false);
        assertEquals(Completeness.COMPLETE,result.completeness());assertEquals(3,calls.get());assertEquals(3,budget.used());
    }
    @Test void repeatedTransientFailureStopsAfterOneRetry() {
        var budget=new ModelBudget();deterministicPartial(service(p->{calls.incrementAndGet();throw new ModelException(ModelException.Code.TRANSIENT_FAILURE,"retry");}).analyze(investigation,budget,false));
        assertEquals(2,calls.get());assertEquals(2,budget.used());
    }
    @Test void repairCannotResetExhaustedBudget() {
        var budget=new ModelBudget(1);deterministicPartial(service("{}").analyze(investigation,budget,false));assertEquals(1,calls.get());assertEquals(1,budget.used());
    }
    @Test void exhaustedSharedBudgetMakesNoProviderCall() {
        var budget=new ModelBudget();for(int n=0;n<6;n++)budget.acquire();deterministicPartial(service(valid()).analyze(investigation,budget,false));assertEquals(0,calls.get());
    }
    @Test void explicitPartialProducesNoModelCall() {
        deterministicPartial(service(valid()).partial(investigation,"Collection interrupted"));assertEquals(0,calls.get());
    }
    @Test void cappedModelContextRetainsFullTimelineAndExplicitLimitation() {
        var more=new ArrayList<Event>();for(int n=0;n<30;n++)more.add(event("more"+n,100+n,"alice","success"));store.ingest(new ImportBatch(more));investigation=accepted();
        var result=service(valid()).analyze(investigation);assertEquals(Completeness.PARTIAL,result.completeness());assertEquals(36,result.timeline().size());
        assertTrue(result.severity().limitations().stream().anyMatch(s->s.contains("24 evidence")));
        var data=(Map<?,?>)JsonCodec.tree(prompts.get(0).content().substring(prompts.get(0).content().indexOf("DATA=")+5));assertEquals(24,((List<?>)data.get("evidence")).size());
    }
    @Test void untrustedOutOfScopeAndForgedEvidenceCannotBeCited() {
        store.ingest(new ImportBatch(List.of(event("bob",10,"bob","success"))));investigation=accepted();
        var scope=investigation.snapshot().scope();investigation.addEvidence(store.search(new ToolCall(UUID.randomUUID(),"logSearch",Map.of("datasetId","demo","datasetVersion",scope.datasetVersion(),"accountId","bob","start",scope.start().toString(),"end",scope.end().toString(),"limit","100"))));
        var bob=investigation.snapshot().evidence().stream().filter(e->e.sourceEventId().equals("bob")).findFirst().orElseThrow();
        var fake=new Evidence(UUID.randomUUID(),"fake",scope.datasetVersion(),"forged","{\"eventId\":\"fake\"}");investigation.addEvidence(new ToolResult("SUCCESS",List.of(fake),scope.datasetVersion(),0,false));
        for(UUID id:List.of(bob.id(),fake.id())) {
            var reply=JsonCodec.encode(new AnalysisService.Synthesis("analysis-v1",List.of(new Hypothesis("Bad citation",List.of(id),List.of(),"LOW",List.of())),List.of(new AnalysisService.Recommendation("REVIEW","Review","LOW",List.of(id),List.of()))));
            assertFalse(service(valid()).validateCitations(reply,investigation));
        }
        var result=service(valid()).analyze(investigation);assertEquals(6,result.timeline().size());assertEquals(Completeness.COMPLETE,result.completeness());
    }
    @Test void citationValidatorUsesProductionCompactParser() {
        assertTrue(service(valid()).validateCitations(valid(),investigation));assertFalse(service(valid()).validateCitations(compact("E99","REVIEW","LOW"),investigation));
        assertFalse(service(valid()).validateCitations("[]",investigation));
    }
    @Test void incompleteCollectionIsPartialEvenWithValidSynthesis() {
        assertEquals(Completeness.PARTIAL,service(valid()).analyze(investigation,new ModelBudget(),true).completeness());
    }
}
