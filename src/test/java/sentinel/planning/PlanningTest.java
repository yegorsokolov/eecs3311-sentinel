package sentinel.planning;

import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import sentinel.data.*;
import sentinel.domain.*;
import sentinel.tools.*;
import static org.junit.jupiter.api.Assertions.*;
import static sentinel.planning.PlanningException.Code.*;

class PlanningTest {
    private static final Instant START = Instant.parse("2026-10-29T05:15:00Z");
    private DatasetStore store;
    private ToolExecutor tools;
    private PlanValidator validator;
    private RulePlanningStrategy rules;
    private final AtomicInteger invoked = new AtomicInteger();

    @BeforeEach void setup() {
        store = new DatasetStore("demo");
        ToolCommand command = call -> { invoked.incrementAndGet(); throw new AssertionError("Planning must not retrieve"); };
        tools = new ToolExecutor(Map.of("logSearch", command, "userActivity", command, "indicatorLookup", command));
        validator = new PlanValidator(tools, store);
        rules = new RulePlanningStrategy(validator, store);
    }
    @AfterEach void cleanup() { tools.close(); }
    private Investigation investigation(String target, TargetType type, String feedVersion) {
        return new Investigation(new Request("Investigate", new CaseScope("demo", store.currentVersion(), target,
                START, START.plusSeconds(60))), type, feedVersion);
    }
    private CaseContext context() { return new CaseContext(investigation("alice", TargetType.ACCOUNT, "feed-1").snapshot(), true); }
    private ToolCall search(Map<String, String> changes) {
        var args = new HashMap<>(Map.of("datasetId", "demo", "datasetVersion", store.currentVersion(), "accountId", "alice",
                "start", START.toString(), "end", START.plusSeconds(60).toString(), "limit", "100"));
        args.putAll(changes);
        return new ToolCall(UUID.randomUUID(), "logSearch", args);
    }
    private Plan plan(List<ToolCall> steps, Map<UUID, List<UUID>> dependencies) {
        return new Plan(UUID.randomUUID(), 0, steps, dependencies, Plan.Origin.MODEL);
    }
    private PlanningException fails(PlanningException.Code code, org.junit.jupiter.api.function.Executable action) {
        var ex = assertThrows(PlanningException.class, action); assertEquals(code, ex.code()); return ex;
    }

    @Test void approvedAccountRulePlanIsLabeledScopedAndDoesNotExecute() {
        var context = context();
        var plan = rules.createPlan(context);
        assertEquals(Plan.Origin.RULE, plan.origin());
        assertEquals(List.of("logSearch", "userActivity"), plan.steps().stream().map(ToolCall::toolName).toList());
        for (var step : plan.steps()) {
            assertEquals("alice", step.args().get("accountId"));
            assertEquals("demo", step.args().get("datasetId"));
            assertEquals("v0", step.args().get("datasetVersion"));
        }
        assertEquals(List.of(plan.steps().get(0).id()), plan.dependencies().get(plan.steps().get(1).id()));
        assertEquals(0, invoked.get());
        assertEquals(CaseStatus.DRAFT, context.snapshot().status());
    }

    @Test void repeatedRulePlanningKeepsSemanticOrderAndArguments() {
        var context = context();
        var first = rules.createPlan(context);
        var second = rules.createPlan(context);
        assertEquals(first.steps().stream().map(ToolCall::toolName).toList(), second.steps().stream().map(ToolCall::toolName).toList());
        assertEquals(first.steps().stream().map(ToolCall::args).toList(), second.steps().stream().map(ToolCall::args).toList());
    }

    @Test void clarificationAndFallbackApprovalAreExplicit() {
        var incomplete = new Investigation(new Request("Investigate", null));
        fails(CLARIFICATION_REQUIRED, () -> rules.createPlan(new CaseContext(incomplete.snapshot(), true)));
        var readyScope = investigation("alice", TargetType.ACCOUNT, "feed-1");
        fails(FALLBACK_APPROVAL_REQUIRED, () -> rules.createPlan(new CaseContext(readyScope.snapshot())));
        var rulePlan = rules.createPlan(new CaseContext(readyScope.snapshot(), true));
        fails(FALLBACK_APPROVAL_REQUIRED, () -> validator.validate(rulePlan, new CaseContext(readyScope.snapshot())));
        assertEquals(0, invoked.get());
    }

    @Test void domainPlanRequiresSelectedFeedAndAvoidsUnsupportedEventSearch() {
        var domain = investigation("EVIL.EXAMPLE.", TargetType.INDICATOR, "feed-1");
        var plan = rules.createPlan(new CaseContext(domain.snapshot(), true));
        assertEquals(1, plan.steps().size());
        assertEquals("indicatorLookup", plan.steps().get(0).toolName());
        assertEquals("evil.example", plan.steps().get(0).args().get("indicator"));
        var noFeed = investigation("evil.example", TargetType.INDICATOR, null);
        fails(CLARIFICATION_REQUIRED, () -> rules.createPlan(new CaseContext(noFeed.snapshot(), true)));
    }

    @Test void ipPlansUseExactSourceFilterAndOptionalEnrichment() {
        var ip = investigation("2001:db8::1", TargetType.INDICATOR, "feed-1");
        var plan = rules.createPlan(new CaseContext(ip.snapshot(), true));
        assertEquals(List.of("logSearch", "indicatorLookup"), plan.steps().stream().map(ToolCall::toolName).toList());
        assertFalse(plan.steps().get(0).args().containsKey("accountId"));
        assertEquals(Indicator.normalize("2001:db8::1"), plan.steps().get(0).args().get("sourceIp"));
        var noFeed = investigation("192.0.2.10", TargetType.INDICATOR, null);
        assertEquals(1, rules.createPlan(new CaseContext(noFeed.snapshot(), true)).steps().size());
    }

    @Test void acceptedEvidenceCanAddOnlyKnownSortedIndicatorStepsWithinEightSteps() {
        var events = new ArrayList<Event>();
        for (int i = 1; i <= 9; i++) events.add(new Event("e" + i, START, "login", "alice", "192.0.2." + i,
                null, null, "file:///sample#/" + i, "pending"));
        store.ingest(new ImportBatch(events));
        var investigation = investigation("alice", TargetType.ACCOUNT, "feed-1");
        investigation.addEvidence(store.search(search(Map.of())));
        var plan = rules.createPlan(new CaseContext(investigation.snapshot(), true));
        assertEquals(8, plan.steps().size());
        assertEquals(List.of("192.0.2.1", "192.0.2.2", "192.0.2.3", "192.0.2.4", "192.0.2.5", "192.0.2.6"),
                plan.steps().subList(2, 8).stream().map(step -> step.args().get("indicator")).toList());
        assertEquals(0, invoked.get());
    }

    @ParameterizedTest @ValueSource(strings={"unknown", "missing", "outside", "version", "interval"})
    void rejectsInvalidToolSchemaOrScope(String variant) {
        ToolCall step = switch (variant) {
            case "unknown" -> new ToolCall(UUID.randomUUID(), "shell", Map.of());
            case "missing" -> new ToolCall(UUID.randomUUID(), "logSearch", Map.of("accountId", "alice"));
            case "outside" -> search(Map.of("accountId", "bob"));
            case "version" -> search(Map.of("datasetVersion", "v999"));
            case "interval" -> search(Map.of("start", START.minusSeconds(1).toString()));
            default -> throw new AssertionError();
        };
        fails(INVALID_PLAN, () -> validator.validate(plan(List.of(step), Map.of()), context()));
        assertEquals(0, invoked.get());
    }

    @Test void semanticDuplicatesAreRejectedDespiteDistinctUuidsAndSpellings() {
        var first = search(Map.of());
        var equivalent = search(Map.of("limit", "0100", "start", "2026-10-29T01:15:00-04:00"));
        fails(INVALID_PLAN, () -> validator.validate(plan(List.of(first, equivalent), Map.of()), context()));
    }

    @ParameterizedTest @ValueSource(strings={"self", "forward", "unknown", "unknown-key", "duplicate", "cycle"})
    void dependenciesMustReferenceUniqueEarlierSteps(String variant) {
        var first = search(Map.of("limit", "1"));
        var second = search(Map.of("limit", "2"));
        Map<UUID, List<UUID>> dependencies = switch (variant) {
            case "self" -> Map.of(first.id(), List.of(first.id()));
            case "forward" -> Map.of(first.id(), List.of(second.id()));
            case "unknown" -> Map.of(second.id(), List.of(UUID.randomUUID()));
            case "unknown-key" -> Map.of(UUID.randomUUID(), List.of(first.id()));
            case "duplicate" -> Map.of(second.id(), List.of(first.id(), first.id()));
            case "cycle" -> Map.of(first.id(), List.of(second.id()), second.id(), List.of(first.id()));
            default -> throw new AssertionError();
        };
        fails(DEPENDENCY_ERROR, () -> validator.validate(plan(List.of(first, second), dependencies), context()));
    }

    @Test void orderingAndCompletionRespectValidatedPrerequisites() {
        var first = search(Map.of("limit", "1"));
        var second = search(Map.of("limit", "2"));
        var plan = validator.validate(plan(List.of(first, second), Map.of(second.id(), List.of(first.id()))), context());
        assertEquals(first, plan.next(Set.of()).orElseThrow());
        assertEquals(second, plan.next(Set.of(first.id())).orElseThrow());
        assertTrue(plan.next(Set.of(first.id(), second.id())).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> plan.next(Set.of(second.id())));
        assertThrows(IllegalArgumentException.class, () -> plan.next(Set.of(UUID.randomUUID())));
    }

    @Test void speculativeLookupCannotAuthorizeItselfThroughASearchDependency() {
        var first = search(Map.of());
        var second = new ToolCall(UUID.randomUUID(), "indicatorLookup",
                Map.of("indicator", "unseen.example", "feedVersion", "feed-1"));
        fails(INVALID_PLAN, () -> validator.validate(plan(List.of(first, second),
                Map.of(second.id(), List.of(first.id()))), context()));
    }

    @Test void revisionsAllowInitialTwoChangesAndRevalidationButNotBudgetBypasses() {
        var investigation = investigation("alice", TargetType.ACCOUNT, null);
        for (int revision = 0; revision <= 2; revision++) {
            var context = new CaseContext(investigation.snapshot(), true);
            Plan plan = rules.createPlan(context);
            assertEquals(revision, plan.revision());
            investigation.setPlan(plan);
            assertEquals(plan, validator.validate(plan, new CaseContext(investigation.snapshot(), true)));
        }
        fails(REVISION_LIMIT, () -> rules.createPlan(new CaseContext(investigation.snapshot(), true)));
        var jump = new Plan(UUID.randomUUID(), 1, List.of(search(Map.of())), Map.of(), Plan.Origin.MODEL);
        fails(INVALID_PLAN, () -> validator.validate(jump, context()));
        assertEquals(0, invoked.get());
    }

    @Test void unknownContextDatasetOrVersionAndFailedCaseAreRejected() {
        fails(INVALID_CONTEXT, () -> validator.validate(plan(List.of(search(Map.of())), Map.of()), null));
        fails(INVALID_PLAN, () -> validator.validate(null, context()));
        var wrong = new Investigation(new Request("Investigate", new CaseScope("other", "v0", "alice", START, START)));
        fails(INVALID_CONTEXT, () -> validator.validate(plan(List.of(search(Map.of())), Map.of()), new CaseContext(wrong.snapshot())));
        var missing = new Investigation(new Request("Investigate", new CaseScope("demo", "v9", "alice", START, START)));
        fails(INVALID_CONTEXT, () -> validator.validate(plan(List.of(search(Map.of())), Map.of()), new CaseContext(missing.snapshot())));
        var investigation = investigation("alice", TargetType.ACCOUNT, null);
        investigation.setPlan(rules.createPlan(new CaseContext(investigation.snapshot(), true)));
        investigation.recordAction("STATE_CHANGED", "RUNNING", List.of());
        investigation.recordAction("STATE_CHANGED", "FAILED", List.of());
        fails(INVALID_CONTEXT, () -> validator.validate(investigation.snapshot().plan(), new CaseContext(investigation.snapshot(), true)));
    }

    @Test void structurallyInvalidPlansAndMutableDependencyInputsAreHandled() {
        var first = search(Map.of());
        assertThrows(IllegalArgumentException.class, () -> plan(Collections.nCopies(9, first), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> plan(List.of(first, first), Map.of()));
        var second = search(Map.of("limit", "2"));
        var prerequisites = new ArrayList<>(List.of(first.id()));
        var dependencies = new HashMap<UUID, List<UUID>>();
        dependencies.put(second.id(), prerequisites);
        var plan = plan(List.of(first, second), dependencies);
        prerequisites.clear(); dependencies.clear();
        assertEquals(List.of(first.id()), plan.dependencies().get(second.id()));
        assertThrows(UnsupportedOperationException.class, () -> plan.dependencies().get(second.id()).clear());
    }

    @Test void validEightStepPlanSpendsNoExecutionAttempts() {
        var steps = new ArrayList<ToolCall>();
        for (int limit = 1; limit <= 8; limit++) steps.add(search(Map.of("limit", Integer.toString(limit))));
        assertEquals(8, validator.validate(plan(steps, Map.of()), context()).steps().size());
        assertEquals(0, invoked.get());
        assertEquals(8, tools.openSession(ToolScope.account(context().snapshot().scope(), "feed-1", Set.of())).attemptsRemaining());
    }

    @Test void tighterExecutorBudgetAlsoConstrainsPlanSize() {
        ToolCommand never = call -> { throw new AssertionError("Validation only"); };
        try (var tight = new ToolExecutor(Map.of("logSearch", never), 1, Duration.ofSeconds(1), 0)) {
            var validator = new PlanValidator(tight, store);
            fails(INVALID_PLAN, () -> validator.validate(plan(
                    List.of(search(Map.of("limit", "1")), search(Map.of("limit", "2"))), Map.of()), context()));
        }
    }}
