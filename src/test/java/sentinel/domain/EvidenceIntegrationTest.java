package sentinel.domain;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class EvidenceIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-10-29T05:15:00Z");
    private static final CaseScope SCOPE = new CaseScope("demo", "v1", "alice", NOW, NOW.plusSeconds(60));
    private Investigation draft() {
        return new Investigation(new Request("Investigate", SCOPE), Clock.fixed(NOW, ZoneOffset.UTC));
    }
    private Evidence evidence(String id) { return new Evidence(UUID.randomUUID(), id, "v1", "file:///sample#/" + id, "{}"); }
    private ToolResult result(Evidence... evidence) { return new ToolResult("SUCCESS", List.of(evidence), "v1", 0, false); }
    private Investigation completed() {
        var investigation = draft();
        investigation.setPlan(new Plan(UUID.randomUUID(), 0, List.of(new ToolCall(UUID.randomUUID(), "logSearch", Map.of()))));
        investigation.record(AuditEntry.transition(NOW, CaseStatus.RUNNING));
        investigation.setAssessment(new Assessment(List.of(), List.of(),
                new Severity(0, SeverityLevel.UNDETERMINED, List.of(), "test-policy", List.of("Fixture only")),
                List.of(), Completeness.COMPLETE));
        investigation.record(AuditEntry.transition(NOW, CaseStatus.COMPLETED));
        return investigation;
    }

    @Test void evidenceIsDeduplicatedAndAuditReferencesAcceptedSources() {
        var investigation = draft();
        var item = evidence("a");
        var before = investigation.snapshot();
        investigation.addEvidence(result(item, item));
        assertEquals(List.of(item), investigation.snapshot().evidence());
        investigation.addEvidence(result(item));
        assertEquals(1, investigation.snapshot().evidence().size());
        assertTrue(before.evidence().isEmpty());
        var audit = investigation.snapshot().history().get(1);
        assertEquals("EVIDENCE_RECORDED", audit.action());
        assertEquals(List.of(item.id()), audit.evidenceIds());
        assertEquals(NOW, audit.timestamp());
        assertEquals(item.provenance(), investigation.snapshot().evidence().get(0).provenance());
    }

    @Test void conflictingBatchCannotPartiallyAttachOrWriteAudit() {
        var investigation = draft();
        var item = evidence("a");
        investigation.addEvidence(result(item));
        var before = investigation.snapshot();
        var conflict = new Evidence(item.id(), item.sourceEventId(), "v1", item.provenance(), "{\"changed\":true}");
        assertThrows(IllegalArgumentException.class, () -> investigation.addEvidence(result(evidence("new"), conflict)));
        assertEquals(before, investigation.snapshot());
    }

    @Test void sourceVersionMismatchRejectsWholeResult() {
        var investigation = draft();
        var before = investigation.snapshot();
        var wrong = new Evidence(UUID.randomUUID(), "wrong", "v2", "source", "{}");
        assertThrows(IllegalArgumentException.class, () -> investigation.addEvidence(result(evidence("new"), wrong)));
        assertEquals(before, investigation.snapshot());
    }

    @Test void newEvidenceStalesAssessmentWithoutRewritingHistoricalSnapshot() {
        var investigation = completed();
        var before = investigation.snapshot();
        investigation.addEvidence(result(evidence("new")));
        assertEquals(Completeness.COMPLETE, before.assessment().completeness());
        assertEquals(Completeness.STALE, investigation.snapshot().assessment().completeness());
        assertEquals(CaseStatus.COMPLETED, investigation.snapshot().status());
    }

    @Test void duplicateAndEmptyResultsDoNotStaleExistingAssessment() {
        var investigation = draft();
        var item = evidence("a");
        investigation.addEvidence(result(item));
        investigation.setPlan(new Plan(UUID.randomUUID(), 0, List.of(new ToolCall(UUID.randomUUID(), "logSearch", Map.of()))));
        investigation.record(AuditEntry.transition(NOW, CaseStatus.RUNNING));
        investigation.setAssessment(new Assessment(List.of(), List.of(),
                new Severity(0, SeverityLevel.UNDETERMINED, List.of(), "test-policy", List.of()),
                List.of(), Completeness.COMPLETE));
        investigation.record(AuditEntry.transition(NOW, CaseStatus.COMPLETED));
        investigation.addEvidence(result(item));
        investigation.addEvidence(new ToolResult("EMPTY", List.of(), "v1", 0, false));
        assertEquals(Completeness.COMPLETE, investigation.snapshot().assessment().completeness());
    }

    @ParameterizedTest @ValueSource(strings={"FAILED", "TIMEOUT", "BOGUS"})
    void failedResultsCannotBecomeEvidence(String status) {
        var investigation = draft();
        var before = investigation.snapshot();
        assertThrows(IllegalArgumentException.class, () -> investigation.addEvidence(
                new ToolResult(status, List.of(), "v1", 0, false)));
        assertEquals(before, investigation.snapshot());
    }

    @Test void emptyAndUnknownCannotSmuggleEvidence() {
        for (String status : List.of("EMPTY", "UNKNOWN")) {
            var investigation = draft();
            assertThrows(IllegalArgumentException.class, () -> investigation.addEvidence(
                    new ToolResult(status, List.of(evidence("a")), "v1", 0, false)));
        }
    }

    @Test void successfulVersionAdvancePreservesEvidenceButInvalidatesPlanAndAssessment() {
        var investigation = completed();
        investigation.addEvidence(result(evidence("retained")));
        var before = investigation.snapshot();
        investigation.recordImport("demo", new Summary("v2", 1, 0, List.of()));
        var after = investigation.snapshot();
        assertEquals("v2", after.scope().datasetVersion());
        assertEquals(CaseStatus.DRAFT, after.status());
        assertNull(after.plan());
        assertEquals(Completeness.STALE, after.assessment().completeness());
        assertEquals(before.evidence(), after.evidence());
        assertEquals("v1", before.scope().datasetVersion());
        assertEquals("IMPORT_COMPLETED", after.history().get(after.history().size() - 1).action());
    }

    @Test void duplicatesAndRejectedImportsPreserveAssessmentAndSelectedVersion() {
        var investigation = completed();
        investigation.recordImport("demo", new Summary("v1", 0, 3, List.of()));
        investigation.recordImport("demo", new Summary("v1", 0, 0, List.of("Invalid input")));
        assertEquals(Completeness.COMPLETE, investigation.snapshot().assessment().completeness());
        assertEquals(CaseStatus.COMPLETED, investigation.snapshot().status());
        assertEquals("v1", investigation.snapshot().scope().datasetVersion());
        assertEquals("IMPORT_REJECTED", investigation.snapshot().history().get(investigation.snapshot().history().size() - 1).action());
    }

    @Test void importPreflightRefusesMissingWrongRunningAndFailedScopes() {
        var incomplete = new Investigation(new Request("Investigate", null));
        assertThrows(IllegalStateException.class, () -> incomplete.validateImport("demo"));
        var investigation = draft();
        assertThrows(IllegalStateException.class, () -> investigation.validateImport("other"));
        investigation.setPlan(new Plan(UUID.randomUUID(), 0, List.of(new ToolCall(UUID.randomUUID(), "logSearch", Map.of()))));
        investigation.record(AuditEntry.transition(NOW, CaseStatus.RUNNING));
        assertThrows(IllegalStateException.class, () -> investigation.validateImport("demo"));
        investigation.record(AuditEntry.transition(NOW, CaseStatus.FAILED));
        assertThrows(IllegalStateException.class, () -> investigation.validateImport("demo"));
        assertThrows(IllegalStateException.class, () -> investigation.addEvidence(result(evidence("a"))));
    }

    @Test void explicitTargetTypeAndFeedSurviveSnapshotsAndRestoration() {
        var scope = new CaseScope("demo", "v1", "EVIL.EXAMPLE.", NOW, NOW.plusSeconds(60));
        var investigation = new Investigation(new Request("Investigate", scope),
                Clock.fixed(NOW, ZoneOffset.UTC), TargetType.INDICATOR, "feed-1");
        var restored = Investigation.restore(investigation.snapshot());
        assertEquals(TargetType.INDICATOR, restored.snapshot().targetType());
        assertEquals("feed-1", restored.snapshot().feedVersion());
        var before = restored.snapshot();
        assertThrows(IllegalArgumentException.class, () -> restored.clarifyScope(SCOPE));
        assertEquals(before, restored.snapshot());
    }

    @Test void progressValuesCopyIdsAndEventBusIsolatesListenerFailures() {
        var ids = new ArrayList<UUID>();
        var event = new ProgressEvent("IMPORT", "SUCCESS", ids);
        ids.add(UUID.randomUUID());
        assertTrue(event.evidenceIds().isEmpty());
        var bus = new sentinel.events.EventBus();
        var seen = new ArrayList<ProgressEvent>();
        sentinel.events.ProgressListener listener = seen::add;
        bus.subscribe(listener);
        bus.subscribe(listener);
        bus.subscribe(value -> { throw new IllegalStateException("View failed"); });
        bus.publish(event);
        assertEquals(List.of(event), seen);
        assertEquals(1, bus.subscriberFailures());
        bus.unsubscribe(listener);
        bus.publish(event);
        assertEquals(1, seen.size());
    }
}
