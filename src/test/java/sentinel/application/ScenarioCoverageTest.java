package sentinel.application;
import java.nio.file.Path;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import sentinel.analysis.*;
import sentinel.data.*;
import sentinel.domain.*;
import sentinel.events.*;
import sentinel.persistence.*;
import sentinel.planning.*;
import sentinel.reporting.*;
import sentinel.tools.*;
import static org.junit.jupiter.api.Assertions.*;
class ScenarioCoverageTest {
    @TempDir Path temp;
    @ParameterizedTest @CsvSource({"account-takeover.json,demo-analyst,7,HIGH,6", "benign.json,benign-user,0,LOW,1"})
    void suppliedScenarioProducesExpectedPolicyAndPersistedGroundedAssessment(String file,String target,int score,SeverityLevel level,int events) {
        var store=new DatasetStore("scenario",Clock.fixed(Instant.parse("2026-10-29T12:00:00Z"),ZoneOffset.UTC),Duration.ofDays(30));store.loadIndicatorFeed(Path.of("data/indicators/threat-feed.json"));
        try(var tools=ToolExecutor.local(store)) {
            var bus=new EventBus();var analysis=new AnalysisService(p->JsonCodec.encode(new AnalysisService.ShortSynthesis("analysis-v1",new AnalysisService.ShortHypothesis("Review synthetic event","E1",List.of(),"LOW","Fixture is not causal proof"),new AnalysisService.ShortRecommendation("REVIEW","Review cited event","E1","LOW","Analyst approval"))),new TimelineBuilder(),new RuleSeverityPolicy(),store);
            var engine=new AgentEngine(c->null,tools,analysis,bus,store);var repo=new JsonCaseRepository(temp.resolve("cases"));var controller=new AgentController(engine,repo,new LogImporter(store),new ReportService(),bus);
            var scope=new CaseScope("scenario","v0",target,Instant.parse("2026-10-22T12:00:00Z"),Instant.parse("2026-10-22T13:00:00Z"));var i=controller.start(new Request("Review supplied synthetic scenario",scope),TargetType.ACCOUNT,"demo-feed-v1",true);
            assertTrue(controller.importLogs(i.snapshot().id(),Path.of("data/scenarios",file)).successful());controller.acceptRulePlan(i.snapshot().id());var assessment=controller.run(i.snapshot().id());
            assertEquals(Completeness.COMPLETE,assessment.completeness());assertEquals(score,assessment.severity().score());assertEquals(level,assessment.severity().level());assertEquals(events,assessment.timeline().size());assertFalse(assessment.hypotheses().get(0).supportingEvidenceIds().isEmpty());controller.save(i.snapshot().id());assertEquals(i.snapshot(),repo.load(i.snapshot().id()).snapshot());
        }
    }
}
