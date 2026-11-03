package sentinel.application;

import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import sentinel.analysis.*;
import sentinel.data.*;
import sentinel.domain.*;
import sentinel.events.*;
import sentinel.llm.*;
import sentinel.persistence.*;
import sentinel.reporting.*;
import sentinel.tools.*;
import static org.junit.jupiter.api.Assertions.*;

class FollowupReportTest {
    @TempDir Path temp;
    private final Instant time=Instant.parse("2026-10-22T12:00:00Z");
    private DatasetStore store;private ToolExecutor tools;private AgentEngine engine;private AgentController controller;private AtomicInteger calls;private List<Prompt> prompts;
    private String mode="valid";private List<String> replies;private AnalysisService analysis;
    @BeforeEach void setup(){store=new DatasetStore("demo");store.ingest(new ImportBatch(List.of(new Event("a",time,"login","alice",null,"host","success","fixture#/0","pending"))));tools=ToolExecutor.local(store);calls=new AtomicInteger();prompts=new ArrayList<>();
        LLMGateway gateway=p->{int n=calls.getAndIncrement();prompts.add(p);if(mode.equals("offline"))throw new ModelException(ModelException.Code.UNAVAILABLE,"private-provider-marker");if(replies!=null)return replies.get(n);if(p.purpose().equals("answer"))return "{\"text\":\"Review the cited authentication event.\",\"evidenceIds\":[\"E1\"],\"limitations\":[\"Synthetic evidence\"]}";return JsonCodec.encode(new AnalysisService.ShortSynthesis("analysis-v1",new AnalysisService.ShortHypothesis("Possible suspicious login","E1",List.of(),"LOW","Not causal proof"),new AnalysisService.ShortRecommendation("REVIEW","Review logins","E1","LOW","Approval")));};
        analysis=new AnalysisService(gateway,new TimelineBuilder(),new RuleSeverityPolicy(),store);engine=new AgentEngine(c->null,tools,analysis,new EventBus(),store);controller=new AgentController(engine,new JsonCaseRepository(temp.resolve("cases")),new LogImporter(store),new ReportService(),new EventBus());}
    @AfterEach void close(){tools.close();}
    private Investigation ready(){var i=controller.start(new Request("Investigate <script>alert(1)</script> [alice]",new CaseScope("demo",store.currentVersion(),"alice",time,time.plusSeconds(60))));controller.acceptRulePlan(i.snapshot().id());return i;}
    private Investigation collected(){var i=ready();controller.run(i.snapshot().id());calls.set(0);prompts.clear();return i;}
    private String valid(){return "{\"text\":\"Review the cited event.\",\"evidenceIds\":[\"E1\"],\"limitations\":[\"Not causal proof\"]}";}
    @Test void citedAnswerResolvesRefsAndPersistsConversationOnReopen(){var i=collected();var answer=controller.ask(i.snapshot().id(),"What supports the assessment?");assertEquals(List.of(i.snapshot().evidence().get(0).id()),answer.evidenceIds());assertEquals(answer,i.snapshot().conversation().get(0).answer());controller.save(i.snapshot().id());assertEquals(answer,controller.open(i.snapshot().id()).snapshot().conversation().get(0).answer());assertEquals(1,calls.get());assertEquals("answer",prompts.get(0).purpose());assertTrue(prompts.get(0).content().contains("Severity is fixed"));}
    @Test void badCitationRepairsExactlyOnce(){var i=collected();replies=List.of(valid().replace("E1","E99"),valid());assertFalse(controller.ask(i.snapshot().id(),"Explain evidence").evidenceIds().isEmpty());assertEquals(2,calls.get());assertTrue(prompts.get(1).content().contains("INVALID_SCHEMA_OR_CITATION"));}
    @ParameterizedTest @ValueSource(strings={"null","[]","{}","not-json","{\"text\":\"Unsupported\",\"evidenceIds\":[],\"limitations\":[]}"})
    void malformedAnswerIsBoundedAndRecordedAsFailure(String reply){var i=collected();replies=List.of(reply,reply);assertThrows(IllegalArgumentException.class,()->controller.ask(i.snapshot().id(),"Explain evidence"));assertEquals(2,calls.get());assertEquals(1,i.snapshot().conversation().size());assertNotNull(i.snapshot().conversation().get(0).failure());assertNull(i.snapshot().conversation().get(0).answer());}
    @Test void noEvidenceAnswerIsExplicitAndDoesNotRequestModel(){var i=ready();var answer=controller.ask(i.snapshot().id(),"Explain evidence");assertTrue(answer.evidenceIds().isEmpty());assertTrue(answer.text().contains("No accepted evidence"));assertEquals(0,calls.get());}
    @Test void scopeWideningIsRejectedWithoutMutationOrModelUse(){var i=collected();var scope=i.snapshot().scope();assertThrows(IllegalArgumentException.class,()->controller.ask(i.snapshot().id(),"Investigate all accounts"));assertEquals(scope,i.snapshot().scope());assertEquals(0,calls.get());assertNotNull(i.snapshot().conversation().get(0).failure());}
    @Test void unavailableProviderIsRecordedWithoutPrivateErrorText(){var i=collected();mode="offline";assertThrows(ModelException.class,()->controller.ask(i.snapshot().id(),"Explain evidence"));assertEquals(1,calls.get());assertEquals("UNAVAILABLE",i.snapshot().conversation().get(0).failure());assertFalse(i.snapshot().conversation().toString().contains("private-provider-marker"));}
    @Test void repairCannotResetProvidedBudget(){var i=collected();replies=List.of("{}");var budget=new ModelBudget(1);assertThrows(ModelException.class,()->analysis.answer(i,"Explain evidence",budget));assertEquals(1,calls.get());assertEquals(1,budget.used());}
    @Test void explicitRefreshCollectsOnlyApprovedSameScopeEvidence(){var i=ready();var scope=i.snapshot().scope();var answer=controller.ask(i.snapshot().id(),"refresh: Explain collected evidence");assertFalse(answer.evidenceIds().isEmpty());assertEquals(scope,i.snapshot().scope());assertEquals(1,i.snapshot().evidence().size());assertEquals(1,calls.get());assertEquals(1,i.snapshot().conversation().size());}
    @Test void runningAndFailedCasesRejectFollowupWithoutModelUse(){var i=ready();i.recordAction("STATE_CHANGED","RUNNING",List.of());assertThrows(IllegalStateException.class,()->controller.ask(i.snapshot().id(),"Explain"));i.recordAction("STATE_CHANGED","FAILED",List.of());assertThrows(IllegalStateException.class,()->controller.ask(i.snapshot().id(),"Explain"));assertEquals(0,calls.get());}
    @Test void reportIsDeterministicGroundedAndEscapesHtml()throws Exception{var i=collected();controller.ask(i.snapshot().id(),"Explain evidence");int before=calls.get();var service=new ReportService();String report=service.preview(i.snapshot());assertEquals(report,service.preview(i.snapshot()));for(String expected:List.of("## Timeline","## Evidence and provenance","## Severity and completeness","## Hypotheses","## Recommendations for human review","## Conversation","## History and failures",i.snapshot().evidence().get(0).id().toString(),"fixture#/0","&lt;script&gt;","\\[alice\\]"))assertTrue(report.contains(expected),expected);assertFalse(report.contains("<script>"));Path p=temp.resolve("report.md");controller.export(i.snapshot().id(),p);assertEquals(report,Files.readString(p));assertEquals(before,calls.get());}
    @Test void exportRequiresExplicitOverwriteAndPreservesExistingFile()throws Exception{var i=ready();Path p=Files.writeString(temp.resolve("report.md"),"keep");assertThrows(IllegalStateException.class,()->controller.export(i.snapshot().id(),p));assertEquals("keep",Files.readString(p));controller.export(i.snapshot().id(),p,true);assertTrue(Files.readString(p).startsWith("# Sentinel investigation report"));assertEquals(0,calls.get());}
    @Test void unassessedReportIsExplicitlyPartialAndIncludesFailureConversation(){var i=ready();assertThrows(IllegalArgumentException.class,()->controller.ask(i.snapshot().id(),"Investigate all accounts"));String report=new ReportService().preview(i.snapshot());assertTrue(report.contains("Unavailable or not yet run; export is partial"));assertTrue(report.contains("ANSWER_UNAVAILABLE_OR_SCOPE_REVISION_REQUIRED"));assertEquals(0,calls.get());}
    @Test void conversationRejectsCitationOutsideAcceptedCaseEvidence(){var i=ready();assertThrows(IllegalArgumentException.class,()->i.recordConversation("Explain",new Answer("Unsupported",List.of(UUID.randomUUID()),List.of()),null));assertTrue(i.snapshot().conversation().isEmpty());}
}
