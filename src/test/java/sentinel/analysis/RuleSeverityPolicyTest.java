package sentinel.analysis;

import java.nio.file.Path;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import sentinel.data.DatasetStore;
import sentinel.domain.*;
import sentinel.tools.IndicatorLookupCommand;
import static org.junit.jupiter.api.Assertions.*;

class RuleSeverityPolicyTest {
    private final Instant start=Instant.parse("2026-10-22T12:00:00Z");
    private final RuleSeverityPolicy policy=new RuleSeverityPolicy();
    private Event event(String id,long seconds,String account,String outcome,String host) {
        return new Event(id,start.plusSeconds(seconds),"login",account,"192.0.2.10",host,outcome,"fixture#/"+id,"pending");
    }
    private List<Event> burst(long last) {
        var rows=new ArrayList<Event>();for(int n=0;n<4;n++)rows.add(event("f"+n,n,"alice","failure","host"));
        rows.add(event("f4",last,"alice","failure","host"));return rows;
    }
    private DatasetStore store(List<Event> events) {
        var store=new DatasetStore("demo",Clock.fixed(start.plusSeconds(86400*7),ZoneOffset.UTC),Duration.ofDays(30));
        store.ingest(new ImportBatch(events));return store;
    }
    private Investigation accepted(DatasetStore store,String feed) {
        var scope=new CaseScope("demo",store.currentVersion(),"alice",start,start.plusSeconds(3600));
        var investigation=new Investigation(new Request("Investigate alice",scope),TargetType.ACCOUNT,feed);
        var args=Map.of("datasetId","demo","datasetVersion",store.currentVersion(),"start",start.toString(),"end",start.plusSeconds(7200).toString(),"limit","1000");
        investigation.addEvidence(store.search(new ToolCall(UUID.randomUUID(),"logSearch",args)));return investigation;
    }
    private Facts facts(List<Event> rows) {var store=store(rows);return RuleSeverityPolicy.derive(store,accepted(store,null).snapshot(),Set.of(),false,false);}
    @Test void allRuleCombinationsUseExactWeightsThresholdsAndStableIds() {
        for(int bits=0;bits<16;bits++) {
            var predicates=new HashMap<String,Boolean>();predicates.put("AUTH",true);int score=0;var ids=new ArrayList<String>();int[] weights={2,3,2,1};
            for(int n=0;n<4;n++)if((bits&(1<<n))!=0){predicates.put("R"+(n+1),true);score+=weights[n];ids.add("R"+(n+1));}
            var result=policy.assess(new Facts(predicates,Map.of(),List.of()));
            assertEquals(score,result.score());assertEquals(ids,result.ruleIds());assertEquals("1",result.policyVersion());
            assertEquals(score<=2?SeverityLevel.LOW:score<=5?SeverityLevel.MEDIUM:SeverityLevel.HIGH,result.level());
        }
    }
    @Test void absentUsableAuthenticationIsUndeterminedEvenWithRuleFlags() {
        var result=policy.assess(new Facts(Map.of("R4",true),Map.of(),List.of("coverage unknown")));
        assertEquals(SeverityLevel.UNDETERMINED,result.level());assertEquals(0,result.score());assertTrue(result.ruleIds().isEmpty());
        assertTrue(result.limitations().contains("coverage unknown"));assertTrue(result.limitations().contains("No usable authentication evidence"));
    }
    @Test void fiveFailuresAtInclusiveTenMinuteBoundaryTriggerR1ButNextSecondDoesNot() {
        assertTrue(facts(burst(600)).predicates().get("R1"));assertFalse(facts(burst(601)).predicates().get("R1"));
        assertFalse(facts(burst(600).subList(0,4)).predicates().get("R1"));
    }
    @Test void successMustFollowBurstStrictlyAndBeWithinTenMinutes() {
        for(long seconds:new long[]{600,601,1200,1201}) {
            var rows=burst(600);rows.add(event("success",seconds,"alice","success","host"));var f=facts(rows);
            assertEquals(seconds==601||seconds==1200,f.predicates().get("R2"));
            if(f.predicates().get("R2")){assertTrue(f.supportingEventIds().get("R2").contains("success"));assertTrue(f.supportingEventIds().get("R2").containsAll(f.supportingEventIds().get("R1")));}
        }
    }
    @Test void failuresFromOtherAccountCannotCompleteBurst() {
        var rows=new ArrayList<>(burst(600).subList(0,4));rows.add(event("bob",5,"bob","failure","host"));
        var f=facts(rows);assertFalse(f.predicates().get("R1"));
    }
    @Test void maliciousIpRequiresAcceptedCanonicalEvidenceFromSelectedFeed() {
        var store=store(List.of(event("a",0,"alice","success","host")));store.loadIndicatorFeed(Path.of("data/indicators/threat-feed.json"));
        var investigation=accepted(store,"demo-feed-v1");
        assertFalse(RuleSeverityPolicy.derive(store,investigation.snapshot(),Set.of(),false,false).predicates().get("R3"));
        var result=new IndicatorLookupCommand(store).execute(new ToolCall(UUID.randomUUID(),"indicatorLookup",Map.of("indicator","192.0.2.10","feedVersion","demo-feed-v1")));
        investigation.addEvidence(result);var f=RuleSeverityPolicy.derive(store,investigation.snapshot(),Set.of(),false,false);
        assertTrue(f.predicates().get("R3"));assertEquals(List.of("a"),f.supportingEventIds().get("R3"));
        var other=accepted(store,"different-feed");other.addEvidence(result);
        assertFalse(RuleSeverityPolicy.derive(store,other.snapshot(),Set.of(),false,false).predicates().get("R3"));
    }
    @Test void forgedMaliciousPayloadCannotAuthorizeSeverityRule() {
        var store=store(List.of(event("a",0,"alice","success","host")));var investigation=accepted(store,"demo-feed-v1");
        var forged=new Evidence(UUID.randomUUID(),"192.0.2.10","demo-feed-v1","forged","{\"verdict\":\"MALICIOUS\"}");
        investigation.addEvidence(new ToolResult("SUCCESS",List.of(forged),"demo-feed-v1",0,false));
        assertFalse(RuleSeverityPolicy.derive(store,investigation.snapshot(),Set.of(),false,false).predicates().get("R3"));
    }
    @Test void criticalHostMustAppearInCanonicalInScopeEvents() {
        var store=store(List.of(event("a",0,"alice","success","host"),event("b",1,"bob","success","other")));
        var investigation=accepted(store,null);
        assertFalse(RuleSeverityPolicy.derive(store,investigation.snapshot(),Set.of("other"),true,false).predicates().get("R4"));
        var f=RuleSeverityPolicy.derive(store,investigation.snapshot(),Set.of("host"),true,false);
        assertTrue(f.predicates().get("R4"));assertEquals(List.of("a"),f.supportingEventIds().get("R4"));
    }
    @Test void partialCoverageAndMissingFeedInventoryAreVisibleLimitations() {
        var store=store(List.of(event("a",0,"alice","success",null)));var f=RuleSeverityPolicy.derive(store,accepted(store,null).snapshot(),Set.of(),false,true);
        assertEquals(3,f.limitations().size());assertTrue(f.limitations().stream().anyMatch(s->s.contains("Partial evidence")));
        assertFalse(f.predicates().get("R3"));assertFalse(f.predicates().get("R4"));
    }
    @Test void unknownOutcomesAndMissingAccountsDoNotBecomeAuthenticationFacts() {
        assertFalse(facts(List.of(event("a",0,null,"success",null),event("b",1,"alice",null,null))).predicates().get("AUTH"));
    }
    @Test void repeatedBurstsDoNotMultiplyRuleWeight() {
        var rows=burst(4);for(int n=5;n<15;n++)rows.add(event("f"+n,n,"alice","failure","host"));
        var result=policy.assess(facts(rows));assertEquals(2,result.score());assertEquals(List.of("R1"),result.ruleIds());
    }
    @Test void factsDefensivelyCopyNestedSupportsAndLimitations() {
        var ids=new ArrayList<>(List.of("a"));var support=new HashMap<String,List<String>>();support.put("R1",ids);
        var predicates=new HashMap<>(Map.of("AUTH",true,"R1",true));var limitations=new ArrayList<>(List.of("partial"));
        var f=new Facts(predicates,support,limitations);ids.clear();support.clear();predicates.clear();limitations.clear();
        assertEquals(List.of("a"),f.supportingEventIds().get("R1"));assertEquals(2,policy.assess(f).score());assertEquals(List.of("partial"),f.limitations());
        assertThrows(UnsupportedOperationException.class,()->f.supportingEventIds().get("R1").clear());
    }
}
