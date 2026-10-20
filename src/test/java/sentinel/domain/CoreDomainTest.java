package sentinel.domain;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class CoreDomainTest {
    private static final Instant START = Instant.parse("2026-10-20T12:00:00Z");
    private static final Instant END = Instant.parse("2026-10-20T13:00:00Z");

    private static Event event() {
        return new Event("event-1", START, "login", null, null, null, null,
                "demo.json#/0", "v1");
    }

    @Test void completeRequestPreservesGoalAndScope() {
        var scope = new CaseScope("demo", "v1", "analyst@example.test", START, END);
        var request = new Request("Investigate suspicious logins", scope);
        assertEquals(scope, request.scope());
        assertEquals("Investigate suspicious logins", request.goal());
    }

    @Test void requestCanAwaitScopeClarification() {
        var request = new Request("Investigate suspicious logins", null);
        assertNull(request.scope());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void scopeRequiresDatasetVersionAndTarget(String missing) {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new CaseScope(missing, "v1", "alice", START, END)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new CaseScope("demo", missing, "alice", START, END)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new CaseScope("demo", "v1", missing, START, END)));
    }

    @Test void scopeRequiresBothTimestamps() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new CaseScope("demo", "v1", "alice", null, END)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new CaseScope("demo", "v1", "alice", START, null)));
    }

    @Test void reversedIntervalRejectedButSingleInstantAllowed() {
        assertThrows(IllegalArgumentException.class,
                () -> new CaseScope("demo", "v1", "alice", END, START));
        assertDoesNotThrow(() -> new CaseScope("demo", "v1", "alice", START, START));
    }

    @Test void offsetTimestampsRepresentTheSameUtcInstant() {
        Instant offset = OffsetDateTime.parse("2026-10-20T08:00:00-04:00").toInstant();
        var scope = new CaseScope("demo", "v1", "alice", offset, START);
        assertEquals(scope.start(), scope.end());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void requestRequiresAnActualGoal(String missing) {
        assertThrows(IllegalArgumentException.class, () -> new Request(missing, null));
    }

    @Test void unavailableEventDetailsRemainUnknown() {
        Event event = event();
        assertAll(
                () -> assertNull(event.accountId()),
                () -> assertNull(event.sourceIp()),
                () -> assertNull(event.hostId()),
                () -> assertNull(event.outcome()),
                () -> assertEquals("demo.json#/0", event.sourcePosition()),
                () -> assertEquals("v1", event.datasetVersion()));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void eventRequiresIdentityTypeAndProvenance(String missing) {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Event(missing, START, "login", null, null, null, null, "demo#/0", "v1")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Event("e1", START, missing, null, null, null, null, "demo#/0", "v1")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Event("e1", START, "login", null, null, null, null, missing, "v1")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Event("e1", START, "login", null, null, null, null, "demo#/0", missing)));
    }

    @Test void eventRequiresTimestamp() {
        assertThrows(IllegalArgumentException.class,
                () -> new Event("e1", null, "login", null, null, null, null, "demo#/0", "v1"));
    }

    @Test void eventPreservesSuppliedTextForSourceTraceability() {
        var event = new Event("event-1", START, "custom-event", "Alice", "192.0.2.1",
                "Host-A", "UNKNOWN", "a file.json#/0", "Version-A");
        assertEquals("Alice", event.accountId());
        assertEquals("Host-A", event.hostId());
        assertEquals("custom-event", event.eventType());
        assertEquals("a file.json#/0", event.sourcePosition());
    }

    @Test void batchCopiesInputAndExposesAnUnmodifiableList() {
        var input = new ArrayList<>(List.of(event()));
        var batch = new ImportBatch(input);
        input.clear();
        assertEquals(1, batch.events().size());
        assertThrows(UnsupportedOperationException.class, () -> batch.events().clear());
    }

    @Test void batchRejectsMissingListAndNullElements() {
        assertThrows(IllegalArgumentException.class, () -> new ImportBatch(null));
        var input = new ArrayList<Event>();
        input.add(null);
        assertThrows(IllegalArgumentException.class, () -> new ImportBatch(input));
    }

    @Test void batchRetainsDuplicatesForTheImporterToCount() {
        var event = event();
        assertEquals(List.of(event, event), new ImportBatch(List.of(event, event)).events());
        assertTrue(new ImportBatch(List.of()).events().isEmpty());
    }
}
