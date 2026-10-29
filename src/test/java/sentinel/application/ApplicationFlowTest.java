package sentinel.application;

import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import sentinel.analysis.*;
import sentinel.data.*;
import sentinel.domain.*;
import sentinel.events.*;
import sentinel.persistence.*;
import sentinel.planning.*;
import sentinel.reporting.*;
import sentinel.tools.*;
import static org.junit.jupiter.api.Assertions.*;

class ApplicationFlowTest {
    @TempDir Path temp;
    private static final Instant START = Instant.parse("2026-10-22T12:00:00Z");
    private DatasetStore store;
    private ToolExecutor tools;
    private AgentEngine engine;
    private AgentController controller;
    private EventBus events;
    private MemoryRepository repository;
    private final List<ProgressEvent> progress = new ArrayList<>();

    private static final class MemoryRepository implements CaseRepository {
        final Map<UUID, CaseSnapshot> saved = new HashMap<>();
        public int save(CaseSnapshot snapshot) { saved.put(snapshot.id(), snapshot); return snapshot.caseVersion(); }
        public Investigation load(UUID id) { return Investigation.restore(Objects.requireNonNull(saved.get(id))); }
        public List<CaseSummary> list() { return List.of(); }
    }

    @BeforeEach void setup() {
        store = new DatasetStore("demo", Clock.fixed(Instant.parse("2026-10-29T05:15:00Z"), ZoneOffset.UTC), Duration.ofDays(30));
        events = new EventBus();
        events.subscribe(progress::add);
        repository = new MemoryRepository();
        tools = ToolExecutor.local(store);
        wire();
    }
    private void wire() {
        var validator = new PlanValidator(tools, store);
        var planner = new RulePlanningStrategy(validator, store);
        var analysis = new AnalysisService(prompt -> { fail("No model calls in these increments"); return ""; },
                new TimelineBuilder(), facts -> { throw new AssertionError("No analysis in this increment"); });
        engine = new AgentEngine(planner, tools, analysis, events, store);
        controller = new AgentController(engine, repository, new LogImporter(store), new ReportService(), events);
    }
    @AfterEach void cleanup() { tools.close(); }
    private CaseScope scope(String version) {
        return new CaseScope("demo", version, "demo-analyst", START, START.plusSeconds(300));
    }
    private Investigation start() { return controller.start(new Request("Investigate demo analyst", scope("v0")), TargetType.ACCOUNT, "demo-feed-v1"); }
    private ToolCall search(String version, Map<String, String> changes) {
        var args = new HashMap<>(Map.of("datasetId", "demo", "datasetVersion", version, "accountId", "demo-analyst",
                "start", START.toString(), "end", START.plusSeconds(300).toString(), "limit", "10"));
        args.putAll(changes);
        return new ToolCall(UUID.randomUUID(), "logSearch", args);
    }
    private ToolCall lookup(String indicator) {
        return new ToolCall(UUID.randomUUID(), "indicatorLookup", Map.of("indicator", indicator, "feedVersion", "demo-feed-v1"));
    }
    private Investigation imported() {
        var investigation = start();
        assertTrue(controller.importLogs(investigation.snapshot().id(), Path.of("data/samples/import-valid.json")).successful());
        return investigation;
    }
    private void complete(Investigation investigation) {
        controller.acceptRulePlan(investigation.snapshot().id());
        investigation.recordAction("STATE_CHANGED", "RUNNING", List.of());
        investigation.setAssessment(new Assessment(List.of(), List.of(),
                new Severity(0, SeverityLevel.UNDETERMINED, List.of(), "test-fixture", List.of()),
                List.of(), Completeness.COMPLETE));
        investigation.recordAction("STATE_CHANGED", "COMPLETED", List.of());
    }
    private ToolException failure(ToolException.Code code, org.junit.jupiter.api.function.Executable action) {
        var ex = assertThrows(ToolException.class, action); assertEquals(code, ex.code()); return ex;
    }

    @Test void clarificationAndExplicitRuleApprovalReuseTheSameDraft() {
        var investigation = controller.start(new Request("Investigate", null));
        UUID id = investigation.snapshot().id();
        assertEquals(CaseStatus.DRAFT, investigation.snapshot().status());
        assertTrue(investigation.snapshot().history().stream().anyMatch(entry -> entry.outcome().equals("CLARIFICATION_REQUIRED")));
        assertNull(controller.clarifyScope(id, scope("v0")));
        assertEquals(id, controller.snapshot(id).id());
        assertTrue(investigation.snapshot().history().stream().anyMatch(entry -> entry.outcome().equals("FALLBACK_APPROVAL_REQUIRED")));
        var plan = controller.acceptRulePlan(id);
        assertEquals(Plan.Origin.RULE, plan.origin());
        assertEquals(CaseStatus.READY, investigation.snapshot().status());
        assertEquals("v0", store.currentVersion());
        assertTrue(investigation.snapshot().evidence().isEmpty());
    }

    @Test void importUpdatesActiveVersionAndInvalidatesOldPlan() {
        var investigation = start();
        UUID id = investigation.snapshot().id();
        controller.acceptRulePlan(id);
        var before = investigation.snapshot();
        var summary = controller.importLogs(id, Path.of("data/samples/import-valid.json"));
        assertEquals(2, summary.accepted());
        assertEquals(1, summary.duplicates());
        assertEquals("v1", investigation.snapshot().scope().datasetVersion());
        assertEquals("v0", before.scope().datasetVersion());
        assertEquals(CaseStatus.DRAFT, investigation.snapshot().status());
        assertNull(investigation.snapshot().plan());
        assertTrue(investigation.snapshot().evidence().isEmpty());
        assertEquals("IMPORT_COMPLETED", investigation.snapshot().history().get(investigation.snapshot().history().size() - 1).action());
        assertEquals("IMPORT", progress.get(progress.size() - 1).stage());
    }

    @Test void duplicateAndInvalidImportsPreserveCompletedAssessmentAndVersion() {
        var investigation = imported();
        complete(investigation);
        UUID id = investigation.snapshot().id();
        var plan = investigation.snapshot().plan();
        var duplicate = controller.importLogs(id, Path.of("data/samples/import-valid.json"));
        assertEquals(0, duplicate.accepted());
        assertEquals(3, duplicate.duplicates());
        assertFalse(controller.importLogs(id, Path.of("data/samples/import-invalid.json")).successful());
        assertFalse(controller.importLogs(id, Path.of("data/samples/import-conflict.json")).successful());
        assertEquals("v1", store.currentVersion());
        assertEquals("v1", investigation.snapshot().scope().datasetVersion());
        assertEquals(plan, investigation.snapshot().plan());
        assertEquals(Completeness.COMPLETE, investigation.snapshot().assessment().completeness());
    }

    @Test void newImportStalesAssessmentAndPreservesPriorEvidence() throws Exception {
        var investigation = imported();
        UUID id = investigation.snapshot().id();
        controller.execute(id, search("v1", Map.of()));
        complete(investigation);
        var previous = investigation.snapshot();
        Path source = temp.resolve("new.json");
        Files.writeString(source, """
                [{"eventId":"new","timestamp":"2026-10-22T12:03:00Z","eventType":"login",
                "accountId":"demo-analyst","sourceIp":"192.0.2.20","outcome":"failure"}]
                """);
        controller.importLogs(id, source);
        assertEquals("v2", investigation.snapshot().scope().datasetVersion());
        assertEquals(Completeness.STALE, investigation.snapshot().assessment().completeness());
        assertEquals(previous.evidence(), investigation.snapshot().evidence());
        assertEquals(Completeness.COMPLETE, previous.assessment().completeness());
        assertEquals(CaseStatus.DRAFT, investigation.snapshot().status());
    }

    @Test void wrongCaseAndInvalidStateAreRejectedBeforeStoreMutation() {
        var investigation = start();
        assertThrows(IllegalArgumentException.class,
                () -> controller.importLogs(UUID.randomUUID(), Path.of("data/samples/import-valid.json")));
        controller.acceptRulePlan(investigation.snapshot().id());
        investigation.recordAction("STATE_CHANGED", "RUNNING", List.of());
        assertThrows(IllegalStateException.class,
                () -> controller.importLogs(investigation.snapshot().id(), Path.of("data/samples/import-valid.json")));
        investigation.recordAction("STATE_CHANGED", "FAILED", List.of());
        assertThrows(IllegalStateException.class,
                () -> controller.importLogs(investigation.snapshot().id(), Path.of("data/samples/import-valid.json")));
        assertEquals("v0", store.currentVersion());
    }

    @Test void invalidNewCaseDoesNotReplaceActiveCase() {
        var investigation = start();
        var wrong = new CaseScope("other", "v0", "x", START, START);
        assertThrows(IllegalArgumentException.class, () -> controller.start(new Request("Other", wrong)));
        assertEquals(investigation.snapshot().id(), controller.snapshot(investigation.snapshot().id()).id());
    }

    @Test void realCommandResultsAttachDeduplicatedEvidenceAndAuditReferences() {
        var investigation = imported();
        UUID id = investigation.snapshot().id();
        var before = investigation.snapshot();
        var result = controller.execute(id, search("v1", Map.of()));
        var args = search("v1", Map.of()).args();
        var activity = controller.execute(id, new ToolCall(UUID.randomUUID(), "userActivity", args));
        assertEquals(2, activity.activitySummary().totalEvents());
        assertEquals(result.evidence(), investigation.snapshot().evidence());
        assertTrue(before.evidence().isEmpty());
        assertTrue(result.evidence().get(0).provenance().contains("import-valid.json"));
        assertEquals(2, investigation.snapshot().history().stream().filter(entry -> entry.action().equals("TOOL_SUCCEEDED")).count());
        assertTrue(investigation.snapshot().history().stream().filter(entry -> entry.action().equals("EVIDENCE_RECORDED"))
                .allMatch(entry -> entry.evidenceIds().size() == 2));
    }

    @Test void emptyAndFailedQueriesRemainDistinctWithoutUnsupportedEvidence() {
        var investigation = imported();
        UUID id = investigation.snapshot().id();
        assertEquals("EMPTY", controller.execute(id, search("v1", Map.of("eventId", "absent"))).status());
        failure(ToolException.Code.OUT_OF_SCOPE, () -> controller.execute(id, search("v1", Map.of("accountId", "other"))));
        assertTrue(investigation.snapshot().evidence().isEmpty());
        assertTrue(investigation.snapshot().history().stream().anyMatch(entry ->
                entry.action().equals("TOOL_FAILED") && entry.outcome().equals("OUT_OF_SCOPE")));
    }

    @Test void sourceValidationRejectsForgedPayloadBeforeCaseMutation() {
        new LogImporter(store).importFile(Path.of("data/samples/import-valid.json"));
        var genuine = store.search(search("v1", Map.of())).evidence().get(0);
        var forged = new Evidence(genuine.id(), genuine.sourceEventId(), genuine.datasetVersion(), genuine.provenance(), "{}");
        tools.close();
        tools = new ToolExecutor(Map.of("logSearch", call -> new ToolResult("SUCCESS", List.of(forged), "v1", 0, false)));
        wire();
        var investigation = controller.start(new Request("Investigate", scope("v1")));
        failure(ToolException.Code.INVALID_RESULT, () -> controller.execute(investigation.snapshot().id(), search("v1", Map.of())));
        assertTrue(investigation.snapshot().evidence().isEmpty());
    }

    @Test void sourceValidationRejectsCanonicalButOutOfTargetEvents() {
        store.ingest(new ImportBatch(List.of(new Event("other", START, "login", "other", "192.0.2.99",
                null, null, "file:///sample#/0", "pending"))));
        var other = store.search(search("v1", Map.of("accountId", "other")));
        tools.close();
        tools = new ToolExecutor(Map.of("logSearch", call -> other));
        wire();
        var investigation = controller.start(new Request("Investigate", scope("v1")));
        failure(ToolException.Code.INVALID_RESULT, () -> controller.execute(investigation.snapshot().id(), search("v1", Map.of())));
        assertTrue(investigation.snapshot().evidence().isEmpty());
    }

    @Test void enrichmentAuthorizationPreservesTheCollectionSessionBudget() {
        var investigation = imported();
        store.loadIndicatorFeed(Path.of("data/indicators/threat-feed.json"));
        var session = engine.beginQuerySession(investigation);
        failure(ToolException.Code.OUT_OF_SCOPE, () -> engine.query(investigation, lookup("192.0.2.10"), session));
        assertEquals(0, session.attemptsUsed());
        engine.query(investigation, search("v1", Map.of()), session);
        assertEquals("MALICIOUS", engine.query(investigation, lookup("192.0.2.10"), session).indicatorMatch().verdict());
        assertEquals(2, session.attemptsUsed());
        failure(ToolException.Code.DUPLICATE_CALL, () -> engine.query(investigation, search("v1", Map.of()), session));
        assertEquals(2, session.attemptsUsed());
        assertEquals(3, investigation.snapshot().evidence().size());
    }

    @Test void importedVersionInvalidatesOutstandingSessions() throws Exception {
        var investigation = imported();
        var session = engine.beginQuerySession(investigation);
        Path source = temp.resolve("new.json");
        Files.writeString(source, "[{\"eventId\":\"new\",\"timestamp\":\"2026-10-22T12:01:00Z\",\"eventType\":\"login\"}]");
        controller.importLogs(investigation.snapshot().id(), source);
        failure(ToolException.Code.OUT_OF_SCOPE, () -> engine.query(investigation, search("v1", Map.of()), session));
        assertEquals(0, session.attemptsUsed());
        assertEquals("v2", investigation.snapshot().scope().datasetVersion());
    }

    @Test void sessionsCannotBeSharedBetweenCasesWithIdenticalScopes() {
        var investigation = imported();
        var session = engine.beginQuerySession(investigation);
        var other = new Investigation(new Request("Other", scope("v1")), TargetType.ACCOUNT, "demo-feed-v1");
        failure(ToolException.Code.OUT_OF_SCOPE, () -> engine.query(other, search("v1", Map.of()), session));
        assertEquals(0, session.attemptsUsed());
    }

    @Test void forgedHistoricalEvidenceDoesNotAuthorizeEnrichment() {
        var investigation = imported();
        investigation.addEvidence(new ToolResult("SUCCESS", List.of(new Evidence(UUID.randomUUID(), "fake", "v1",
                "file:///fake", "{\"sourceIp\":\"untrusted.example\"}")), "v1", 0, false));
        assertTrue(CaseEvidence.indicators(store, investigation.snapshot()).isEmpty());
        var session = engine.beginQuerySession(investigation);
        failure(ToolException.Code.OUT_OF_SCOPE, () -> engine.query(investigation, lookup("untrusted.example"), session));
        assertEquals(0, session.attemptsUsed());
    }

    @Test void revisedTargetsDoNotInheritUnrelatedIndicatorAuthorization() {
        var investigation = imported();
        controller.execute(investigation.snapshot().id(), search("v1", Map.of()));
        complete(investigation);
        investigation.reviseScope(new CaseScope("demo", "v1", "other", START, START.plusSeconds(300)));
        assertTrue(CaseEvidence.indicators(store, investigation.snapshot()).isEmpty());
    }

    @Test void controllerRejectsMismatchedServiceStores() {
        assertThrows(IllegalArgumentException.class, () -> new AgentController(engine, repository,
                new LogImporter(new DatasetStore("demo")), new ReportService(), events));
    }

    @Test void observerFailureCannotUndoAcceptedImport() {
        events.subscribe(event -> { throw new IllegalStateException("Broken view"); });
        var investigation = imported();
        assertEquals("v1", investigation.snapshot().scope().datasetVersion());
        assertEquals(2, store.events("v1").size());
        assertTrue(events.subscriberFailures() >= 1);
    }

    @Test void repositoryRoutingPreservesContextAndUnavailableDataRemainsViewable() {
        var scope = new CaseScope("demo", "v99", "evil.example", START, START.plusSeconds(300));
        var archived = new Investigation(new Request("Investigate", scope), TargetType.INDICATOR, "feed-1");
        repository.save(archived.snapshot());
        var opened = controller.open(archived.snapshot().id());
        assertEquals(TargetType.INDICATOR, opened.snapshot().targetType());
        assertEquals("feed-1", opened.snapshot().feedVersion());
        failure(ToolException.Code.UNKNOWN_VERSION, () -> engine.beginQuerySession(opened));
        assertEquals(opened.snapshot().caseVersion(), controller.save(opened.snapshot().id()));
    }

    @Test void missingScopeQueryRecordsFailureWithoutRetrieval() {
        var investigation = controller.start(new Request("Investigate", null));
        failure(ToolException.Code.SCOPE_REQUIRED, () -> controller.execute(investigation.snapshot().id(), search("v0", Map.of())));
        assertTrue(investigation.snapshot().evidence().isEmpty());
        assertEquals("SCOPE_REQUIRED", investigation.snapshot().history().get(investigation.snapshot().history().size() - 1).outcome());
    }

    @Test void aRunningCaseCannotBeReplacedByStartingAnother() {
        var investigation = start();
        controller.acceptRulePlan(investigation.snapshot().id());
        investigation.recordAction("STATE_CHANGED", "RUNNING", List.of());
        assertThrows(IllegalStateException.class, () -> controller.start(new Request("Other", scope("v0"))));
        assertEquals(investigation.snapshot().id(), controller.snapshot(investigation.snapshot().id()).id());
    }

    @Test void engineRevalidatesPlannerOutputInsteadOfTrustingStrategyClaims() {
        var investigation = imported();
        var bad = new Plan(UUID.randomUUID(), 0, List.of(search("v1", Map.of("accountId", "other"))), Map.of(), Plan.Origin.MODEL);
        var analysis = new AnalysisService(prompt -> "", new TimelineBuilder(), facts -> null);
        var unsafe = new AgentEngine(context -> bad, tools, analysis, events, store);
        assertNull(unsafe.plan(new Request(investigation.snapshot().goal(), investigation.snapshot().scope()), investigation));
        assertEquals(CaseStatus.DRAFT, investigation.snapshot().status());
        assertNull(investigation.snapshot().plan());
        assertTrue(investigation.snapshot().history().stream().anyMatch(entry -> entry.outcome().equals("INVALID_PLAN")));
    }}
