package sentinel.domain;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

class InvestigationTest {
    private static final Instant NOW = Instant.parse("2026-10-21T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final CaseScope SCOPE = new CaseScope("demo", "v1", "alice", NOW, NOW.plusSeconds(3600));

    private static Investigation draft() {
        return new Investigation(new Request("Investigate alice", SCOPE), CLOCK);
    }

    private static Plan plan() {
        return new Plan(UUID.randomUUID(), 0,
                List.of(new ToolCall(UUID.randomUUID(), "logSearch", Map.of("accountId", "alice"))));
    }

    private static Investigation running() {
        Investigation caseFile = draft();
        caseFile.setPlan(plan());
        caseFile.record(AuditEntry.transition(NOW, CaseStatus.RUNNING));
        return caseFile;
    }

    private static Assessment assessment(Completeness completeness) {
        return new Assessment(List.of(), List.of(),
                new Severity(0, SeverityLevel.UNDETERMINED, List.of(), "1", List.of("No authentication evidence")),
                List.of(), completeness);
    }

    private static Investigation completed() {
        Investigation caseFile = running();
        // This fixture represents analysis accepted by the future AnalysisService.
        caseFile.setAssessment(assessment(Completeness.COMPLETE));
        caseFile.record(AuditEntry.transition(NOW, CaseStatus.COMPLETED));
        return caseFile;
    }

    @Test void newCaseHasIdentityGoalAndDraftHistory() {
        CaseSnapshot snapshot = draft().snapshot();
        assertNotNull(snapshot.id());
        assertEquals("Investigate alice", snapshot.goal());
        assertEquals(CaseStatus.DRAFT, snapshot.status());
        assertEquals(0, snapshot.caseVersion());
        assertEquals("CREATED", snapshot.history().get(0).action());
        assertEquals(NOW, snapshot.history().get(0).timestamp());
        assertNull(snapshot.modelVersion());
    }

    @Test void missingScopeRequiresClarificationBeforeReady() {
        Investigation caseFile = new Investigation(new Request("Investigate", null), CLOCK);
        CaseSnapshot before = caseFile.snapshot();
        assertThrows(IllegalStateException.class, () -> caseFile.setPlan(plan()));
        assertEquals(before, caseFile.snapshot());
        caseFile.clarifyScope(SCOPE);
        caseFile.setPlan(plan());
        assertEquals(CaseStatus.READY, caseFile.snapshot().status());
    }

    @Test void acceptingPlanDoesNotRunAndSnapshotsDoNotChange() {
        Investigation caseFile = draft();
        CaseSnapshot before = caseFile.snapshot();
        caseFile.setPlan(plan());
        assertEquals(CaseStatus.DRAFT, before.status());
        assertNull(before.plan());
        assertEquals(1, before.history().size());
        assertEquals(CaseStatus.READY, caseFile.snapshot().status());
        assertEquals(1, caseFile.snapshot().caseVersion());
        assertThrows(UnsupportedOperationException.class, () -> before.history().clear());
    }

    @ParameterizedTest
    @EnumSource(value = CaseStatus.class, names = {"READY", "RUNNING", "COMPLETED", "PARTIAL", "FAILED", "PAUSED"})
    void draftCannotSkipPlanValidation(CaseStatus target) {
        Investigation caseFile = draft();
        CaseSnapshot before = caseFile.snapshot();
        assertThrows(IllegalStateException.class,
                () -> caseFile.record(AuditEntry.transition(NOW, target)));
        assertEquals(before, caseFile.snapshot());
    }

    @Test void runRequiresReadyAndCompletionRequiresAcceptedAnalysis() {
        Investigation caseFile = running();
        CaseSnapshot before = caseFile.snapshot();
        assertThrows(IllegalStateException.class,
                () -> caseFile.record(AuditEntry.transition(NOW, CaseStatus.COMPLETED)));
        assertEquals(before, caseFile.snapshot());
        caseFile.setAssessment(assessment(Completeness.PARTIAL));
        assertThrows(IllegalStateException.class,
                () -> caseFile.record(AuditEntry.transition(NOW, CaseStatus.COMPLETED)));
        caseFile.setAssessment(assessment(Completeness.COMPLETE));
        assertEquals(CaseStatus.RUNNING, caseFile.snapshot().status());
        caseFile.record(AuditEntry.transition(NOW, CaseStatus.COMPLETED));
        assertEquals(CaseStatus.COMPLETED, caseFile.snapshot().status());
    }

    @ParameterizedTest
    @EnumSource(value = CaseStatus.class, names = {"PARTIAL", "FAILED"})
    void interruptedRunsPreserveAvailableResultsAndHistory(CaseStatus target) {
        Investigation caseFile = running();
        Assessment result = assessment(Completeness.PARTIAL);
        caseFile.setAssessment(result);
        caseFile.record(AuditEntry.transition(NOW, target));
        assertEquals(result, caseFile.snapshot().assessment());
        assertEquals(target, caseFile.snapshot().status());
        assertEquals(5, caseFile.snapshot().history().size());
    }

    @Test void partialNeedsExplicitRevalidationBeforeRunningAgain() {
        Investigation caseFile = running();
        caseFile.record(AuditEntry.transition(NOW, CaseStatus.PARTIAL));
        assertThrows(IllegalStateException.class,
                () -> caseFile.record(AuditEntry.transition(NOW, CaseStatus.RUNNING)));
        caseFile.setPlan(plan());
        assertEquals(CaseStatus.READY, caseFile.snapshot().status());
    }

    @Test void failedIsTerminalButStillReadableAndAuditable() {
        Investigation caseFile = running();
        caseFile.record(AuditEntry.transition(NOW, CaseStatus.FAILED));
        assertThrows(IllegalStateException.class, () -> caseFile.setPlan(plan()));
        caseFile.record(new AuditEntry(NOW, "VIEWED", "FAILURE_REVIEWED", List.of()));
        assertEquals(CaseStatus.FAILED, caseFile.snapshot().status());
    }

    @Test void completedCanRevalidateOrReturnToDraftForScopeClarification() {
        Investigation caseFile = completed();
        CaseSnapshot before = caseFile.snapshot();
        caseFile.setPlan(plan());
        assertEquals(CaseStatus.READY, caseFile.snapshot().status());
        assertEquals(Completeness.STALE, caseFile.snapshot().assessment().completeness());
        assertEquals(Completeness.COMPLETE, before.assessment().completeness());
        Investigation revised = completed();
        revised.reviseScope(null);
        assertEquals(CaseStatus.DRAFT, revised.snapshot().status());
        assertNull(revised.snapshot().plan());
        assertNull(revised.snapshot().scope());
        assertEquals(Completeness.STALE, revised.snapshot().assessment().completeness());
    }

    @Test void runningSnapshotRestoresPausedWithoutMutatingSavedValue() {
        CaseSnapshot saved = running().snapshot();
        Investigation restored = Investigation.restore(saved, CLOCK);
        assertEquals(CaseStatus.RUNNING, saved.status());
        assertEquals(CaseStatus.PAUSED, restored.snapshot().status());
        assertEquals(saved.id(), restored.snapshot().id());
        assertEquals(saved.goal(), restored.snapshot().goal());
        assertEquals(saved.caseVersion() + 1, restored.snapshot().caseVersion());
        assertThrows(IllegalStateException.class,
                () -> restored.record(AuditEntry.transition(NOW, CaseStatus.RUNNING)));
        restored.setPlan(plan());
        assertEquals(CaseStatus.READY, restored.snapshot().status());
    }

    @Test void nonRunningSnapshotRoundTripsWithoutRevisionChange() {
        CaseSnapshot saved = completed().snapshot();
        assertEquals(saved, Investigation.restore(saved, CLOCK).snapshot());
    }

    @Test void auditCannotReferToUnknownEvidenceOrChangeStateByAccident() {
        Investigation caseFile = draft();
        CaseSnapshot before = caseFile.snapshot();
        assertThrows(IllegalArgumentException.class,
                () -> caseFile.record(new AuditEntry(NOW, "QUERY", "SUCCESS", List.of(UUID.randomUUID()))));
        assertEquals(before, caseFile.snapshot());
        caseFile.record(new AuditEntry(NOW, "NOTE", "COMPLETED", List.of()));
        assertEquals(CaseStatus.DRAFT, caseFile.snapshot().status());
    }

    @Test void invalidLifecycleNameDoesNotMutateCase() {
        Investigation caseFile = draft();
        CaseSnapshot before = caseFile.snapshot();
        assertThrows(IllegalArgumentException.class,
                () -> caseFile.record(new AuditEntry(NOW, "STATE_CHANGED", "INVALID", List.of())));
        assertEquals(before, caseFile.snapshot());
    }

    @Test void invalidAssessmentLeavesRunningCaseUntouched() {
        Investigation caseFile = running();
        CaseSnapshot before = caseFile.snapshot();
        var hypothesis = new Hypothesis("Candidate", List.of(UUID.randomUUID()), List.of(), "low", List.of());
        var invalid = new Assessment(List.of(), List.of(hypothesis), assessment(Completeness.PARTIAL).severity(),
                List.of(), Completeness.PARTIAL);
        assertThrows(IllegalArgumentException.class, () -> caseFile.setAssessment(invalid));
        assertEquals(before, caseFile.snapshot());
        assertThrows(IllegalArgumentException.class,
                () -> caseFile.setAssessment(assessment(Completeness.STALE)));
        assertThrows(IllegalStateException.class, () -> draft().setAssessment(assessment(Completeness.COMPLETE)));
    }

    @Test void planAndNestedArgumentsAreIndependentOfCallerCollections() {
        var args = new HashMap<String, String>();
        args.put("accountId", "alice");
        var call = new ToolCall(UUID.randomUUID(), "logSearch", args);
        var steps = new ArrayList<>(List.of(call));
        var accepted = new Plan(UUID.randomUUID(), 0, steps);
        Investigation caseFile = draft();
        caseFile.setPlan(accepted);
        CaseSnapshot saved = caseFile.snapshot();
        args.put("accountId", "mallory");
        steps.clear();
        assertEquals("alice", saved.plan().steps().get(0).args().get("accountId"));
        assertThrows(UnsupportedOperationException.class, () -> saved.plan().steps().clear());
        assertThrows(UnsupportedOperationException.class, () -> call.args().clear());
    }

    @Test void assessmentAndNestedListsAreIndependentOfCallerCollections() {
        var rules = new ArrayList<>(List.of("R1"));
        var limitations = new ArrayList<>(List.of("Partial evidence"));
        var hypotheses = new ArrayList<Hypothesis>();
        var recommendations = new ArrayList<>(List.of("Review"));
        var result = new Assessment(List.of(), hypotheses,
                new Severity(2, SeverityLevel.LOW, rules, "1", limitations), recommendations, Completeness.PARTIAL);
        Investigation caseFile = running();
        caseFile.setAssessment(result);
        CaseSnapshot saved = caseFile.snapshot();
        rules.clear();
        limitations.clear();
        recommendations.clear();
        hypotheses.add(new Hypothesis("Later", List.of(), List.of(), "low", List.of()));
        assertEquals(List.of("R1"), saved.assessment().severity().ruleIds());
        assertEquals(List.of("Review"), saved.assessment().recommendations());
        assertTrue(saved.assessment().hypotheses().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> saved.assessment().severity().limitations().clear());
    }

    @Test void snapshotRejectsUnsupportedSchemaAndInconsistentState() {
        CaseSnapshot saved = draft().snapshot();
        assertThrows(IllegalArgumentException.class, () -> copy(saved, 2, CaseStatus.DRAFT, null, List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> copy(saved, 1, CaseStatus.RUNNING, null, List.of(), List.of()));
    }

    @Test void snapshotRejectsDuplicateEvidenceAndDanglingConversationCitations() {
        CaseSnapshot saved = draft().snapshot();
        var item = new Evidence(UUID.randomUUID(), "e1", "v1", "demo#/0", "{}");
        assertThrows(IllegalArgumentException.class,
                () -> copy(saved, 1, CaseStatus.DRAFT, null, List.of(item, item), List.of()));
        var question = new ConversationEntry(NOW, "Why?", new Answer("Because", List.of(item.id()), List.of()), null);
        assertThrows(IllegalArgumentException.class,
                () -> copy(saved, 1, CaseStatus.DRAFT, null, List.of(), List.of(question)));
        var events = new ArrayList<>(List.of(question));
        CaseSnapshot valid = copy(saved, 1, CaseStatus.DRAFT, null, List.of(item), events);
        events.clear();
        assertEquals(1, valid.conversation().size());
        assertThrows(UnsupportedOperationException.class, () -> valid.conversation().get(0).answer().evidenceIds().clear());
    }

    @Test void restoringAndFailingPreservesEvidenceAndVersionMetadata() {
        var base = running().snapshot();
        var item = new Evidence(UUID.randomUUID(), "e1", "v1", "demo#/0", "{}");
        var saved = new CaseSnapshot(1, base.caseVersion(), base.id(), base.goal(), base.status(),
                base.scope(), base.plan(), List.of(item), null, List.of(), base.history(),
                "model-test", "prompt-v1", "policy-v1");
        var restored = Investigation.restore(saved, CLOCK);
        restored.setPlan(plan());
        restored.record(AuditEntry.transition(NOW, CaseStatus.RUNNING));
        restored.record(AuditEntry.transition(NOW, CaseStatus.FAILED));
        assertEquals(List.of(item), restored.snapshot().evidence());
        assertEquals("model-test", restored.snapshot().modelVersion());
        assertEquals("prompt-v1", restored.snapshot().promptVersion());
        assertEquals("policy-v1", restored.snapshot().policyVersion());
        assertEquals(saved.history(), restored.snapshot().history().subList(0, saved.history().size()));
    }

    private static CaseSnapshot copy(CaseSnapshot saved, int schema, CaseStatus status, Plan plan,
                                     List<Evidence> evidence, List<ConversationEntry> conversation) {
        return new CaseSnapshot(schema, saved.caseVersion(), saved.id(), saved.goal(), status, saved.scope(), plan,
                evidence, null, conversation, saved.history(), null, null, null);
    }
}
