package sentinel.data;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import sentinel.domain.*;
import sentinel.tools.*;
import static org.junit.jupiter.api.Assertions.*;

class UserActivityTest {
    private static final Instant START = Instant.parse("2026-10-22T12:00:00Z");
    private final DatasetStore store = new DatasetStore("demo");

    private Event event(String id, long seconds, String account, String host, String outcome) {
        return new Event(id, START.plusSeconds(seconds), id.equals("z") ? "file-access" : "login",
                account, null, host, outcome, "file:///activity.json#/" + id, "pending");
    }
    private ToolCall call(Map<String, String> overrides) {
        var args = new HashMap<>(Map.of("datasetId", "demo", "datasetVersion", "v1",
                "start", START.toString(), "end", START.plusSeconds(60).toString(),
                "limit", "10", "accountId", "alice"));
        args.putAll(overrides);
        return new ToolCall(UUID.randomUUID(), "userActivity", args);
    }
    private void seed() {
        store.ingest(new ImportBatch(List.of(
                event("z", 60, "alice", "host-2", null),
                event("b", 0, "alice", "host-1", "failure"),
                event("a", 0, "alice", "host-1", "success"),
                event("c", 30, "alice", null, "SUCCESS"),
                event("other", 30, "bob", "host-3", "success"),
                event("anonymous", 30, null, null, null),
                event("before", -1, "alice", "host-3", "failure"),
                event("after", 61, "alice", "host-3", "failure"))));
    }

    @Test void ordersAcrossEventTypesAndIncludesIntervalBoundaries() {
        seed();
        var result = new UserActivityCommand(store).execute(call(Map.of()));
        assertEquals(List.of("a", "b", "c", "z"),
                result.evidence().stream().map(Evidence::sourceEventId).toList());
        assertEquals("SUCCESS", result.status());
        assertFalse(result.truncated());
        assertEquals("v1", result.sourceVersion());
        assertTrue(result.durationMillis() >= 0);
        var summary = result.activitySummary();
        assertNotNull(summary); // The shared command workflow must preserve structured data.
        assertEquals("alice", summary.accountId());
        assertEquals(START, summary.start());
        assertEquals(START.plusSeconds(60), summary.end());
        assertEquals(4, summary.totalEvents());
        assertEquals(4, summary.returnedEvents());
        assertEquals(1, summary.successes());
        assertEquals(1, summary.failures());
        assertEquals(2, summary.unknownOutcomes());
        assertEquals(2, summary.distinctHosts());
        assertEquals(1, summary.unknownHostEvents());
        assertEquals(START, summary.firstEvent());
        assertEquals(START.plusSeconds(60), summary.lastEvent());
    }

    @Test void cappedEvidenceRetainsFullIntervalCountsAndStatesReturnedCoverage() {
        seed();
        var result = new UserActivityCommand(store).execute(call(Map.of("limit", "1")));
        assertEquals("PARTIAL", result.status());
        assertTrue(result.truncated());
        assertEquals(List.of("a"), result.evidence().stream().map(Evidence::sourceEventId).toList());
        assertEquals(4, result.activitySummary().totalEvents());
        assertEquals(1, result.activitySummary().returnedEvents());
        assertEquals(2, result.activitySummary().distinctHosts());
        assertEquals(START.plusSeconds(60), result.activitySummary().lastEvent());
        assertFalse(store.activity(call(Map.of("limit", "4"))).truncated());
    }

    @Test void unknownAccountIsEmptyWithExplicitIntervalAndZeroCounts() {
        seed();
        var result = store.activity(call(Map.of("accountId", "Alice")));
        assertEquals("EMPTY", result.status());
        assertFalse(result.truncated());
        assertTrue(result.evidence().isEmpty());
        assertEquals(0, result.activitySummary().totalEvents());
        assertEquals(0, result.activitySummary().distinctHosts());
        assertEquals(0, result.activitySummary().unknownOutcomes());
        assertEquals(START, result.activitySummary().start());
        assertNull(result.activitySummary().firstEvent());
        assertNull(result.activitySummary().lastEvent());
    }

    @Test void emptyVersionCanBeQueried() {
        assertEquals("EMPTY", store.activity(call(Map.of("datasetVersion", "v0"))).status());
    }

    @Test void singleInstantAndUtcOffsetAreSupported() {
        seed();
        var result = store.activity(call(Map.of("start", "2026-10-22T08:00:00-04:00",
                "end", START.toString())));
        assertEquals(2, result.activitySummary().totalEvents());
        assertEquals(START, result.activitySummary().firstEvent());
        assertEquals(START, result.activitySummary().lastEvent());
    }

    @Test void versionsAndEvidenceRemainStableWithoutMutation() {
        seed();
        var before = store.activity(call(Map.of()));
        var searchArgs = new HashMap<>(call(Map.of()).args());
        var search = store.search(new ToolCall(UUID.randomUUID(), "logSearch", searchArgs));
        assertEquals(search.evidence(), before.evidence());
        assertNull(search.activitySummary());
        store.ingest(new ImportBatch(List.of(event("new", 10, "alice", "host-4", "success"))));
        var old = store.activity(call(Map.of()));
        assertEquals(before.evidence(), old.evidence());
        assertEquals(before.activitySummary(), old.activitySummary());
        var newer = store.activity(call(Map.of("datasetVersion", "v2")));
        assertEquals(5, newer.activitySummary().totalEvents());
        assertEquals(3, newer.activitySummary().distinctHosts());
        assertEquals("v2", store.currentVersion());
        assertEquals(8, store.events("v1").size());
        assertEquals("file:///activity.json#/a", old.evidence().get(0).provenance());
        assertThrows(UnsupportedOperationException.class, () -> old.evidence().clear());
    }

    @ParameterizedTest @ValueSource(strings={"0", "-1", "1001", "1.5", "2147483648", "all"})
    void rejectsInvalidLimits(String limit) {
        assertInvalid(call(Map.of("limit", limit)));
    }

    @ParameterizedTest @ValueSource(strings={"2026-10-22T12:00:00", "not-time", "2026-02-30T12:00:00Z"})
    void rejectsInvalidTimes(String time) {
        assertInvalid(call(Map.of("start", time)));
    }

    @ParameterizedTest @ValueSource(strings={"datasetId", "datasetVersion", "start", "end", "limit", "accountId"})
    void rejectsMissingAndBlankRequiredArguments(String field) {
        var args = new HashMap<>(call(Map.of()).args());
        args.remove(field);
        assertInvalid(new ToolCall(UUID.randomUUID(), "userActivity", args));
        assertInvalid(call(Map.of(field, " ")));
    }

    @Test void rejectsWrongToolNullExtraFiltersAndReversedInterval() {
        assertInvalid(null);
        assertInvalid(new ToolCall(UUID.randomUUID(), "logSearch", call(Map.of()).args()));
        assertInvalid(call(Map.of("eventType", "login")));
        assertInvalid(call(Map.of("end", START.minusSeconds(1).toString())));
        assertThrows(ToolException.class, () -> new UserActivityCommand(store).execute(null));
    }

    @Test void distinguishesUnknownDatasetAndVersion() {
        seed();
        assertEquals(ToolException.Code.UNKNOWN_DATASET, assertThrows(ToolException.class,
                () -> store.activity(call(Map.of("datasetId", "other")))).code());
        assertEquals(ToolException.Code.UNKNOWN_VERSION, assertThrows(ToolException.class,
                () -> store.activity(call(Map.of("datasetVersion", "v99")))).code());
        assertEquals("v1", store.currentVersion());
    }

    @Test void summaryRejectsInconsistentCountsAndCoverage() {
        assertThrows(IllegalArgumentException.class, () -> new ActivitySummary("alice", START, START,
                1, 2, 1, 0, 0, 1, 0, START, START));
        assertThrows(IllegalArgumentException.class, () -> new ActivitySummary("alice", START, START,
                1, 1, 0, 0, 0, 1, 0, START, START));
        assertThrows(IllegalArgumentException.class, () -> new ActivitySummary("alice", START, START,
                0, 0, 0, 0, 0, 0, 0, START, START));
        assertThrows(IllegalArgumentException.class, () -> new ActivitySummary("alice", START, START,
                1, 1, 1, 0, 0, 1, 0, null, null));
        assertThrows(IllegalArgumentException.class, () -> new ActivitySummary("alice", START, START,
                1, 1, 1, 0, 0, 1, 1, START, START));
    }

    private void assertInvalid(ToolCall call) {
        assertEquals(ToolException.Code.INVALID_ARGUMENT,
                assertThrows(ToolException.class, () -> store.activity(call)).code());
    }
}
