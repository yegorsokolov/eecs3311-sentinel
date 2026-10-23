package sentinel.data;

import com.fasterxml.jackson.core.JsonFactory;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import sentinel.domain.*;
import sentinel.tools.*;
import static org.junit.jupiter.api.Assertions.*;

class LogSearchTest {
    private final DatasetStore store = new DatasetStore("demo");
    private static final Instant START = Instant.parse("2026-10-22T12:00:00Z");

    private Event event(String id, long seconds, String account) {
        return new Event(id, START.plusSeconds(seconds), "login", account, "192.0.2.10",
                null, "failure", "file:///sample.json#/" + id, "pending");
    }
    private ToolCall call(Map<String, String> overrides) {
        var args = new HashMap<>(Map.of("datasetId", "demo", "datasetVersion", "v1",
                "start", START.toString(), "end", START.plusSeconds(60).toString(), "limit", "10"));
        args.putAll(overrides);
        return new ToolCall(UUID.randomUUID(), "logSearch", args);
    }
    private void seed() {
        store.ingest(new ImportBatch(List.of(event("z", 60, "alice"), event("b", 0, "alice"),
                event("a", 0, "bob"), event("outside", 61, "alice"))));
    }
    private List<String> ids(ToolResult result) {
        return result.evidence().stream().map(Evidence::sourceEventId).toList();
    }

    @Test void ordersByTimeThenIdAndIncludesBothBoundaries() {
        seed();
        ToolResult result = new LogSearchCommand(store).execute(call(Map.of()));
        assertEquals(List.of("a", "b", "z"), ids(result));
        assertEquals("SUCCESS", result.status());
        assertFalse(result.truncated());
        assertTrue(result.durationMillis() >= 0);
        assertEquals("v1", result.sourceVersion());
    }

    @Test void filtersAreCombinedExactlyAndMissingFieldsDoNotMatch() {
        seed();
        assertEquals(List.of("b", "z"), ids(store.search(call(Map.of("accountId", "alice",
                "outcome", "failure", "eventType", "login", "sourceIp", "192.0.2.10")))));
        assertTrue(store.search(call(Map.of("accountId", "Alice"))).evidence().isEmpty());
        assertTrue(store.search(call(Map.of("hostId", "unknown"))).evidence().isEmpty());
        assertEquals(List.of("b"), ids(store.search(call(Map.of("eventId", "b")))));
    }

    @Test void capsOnlyWhenAnAdditionalMatchExists() {
        seed();
        ToolResult limited = store.search(call(Map.of("limit", "2")));
        assertEquals(List.of("a", "b"), ids(limited));
        assertTrue(limited.truncated());
        assertEquals("PARTIAL", limited.status());
        assertFalse(store.search(call(Map.of("limit", "3"))).truncated());
    }

    @Test void emptyIsSuccessfulReadAndVersionZeroCanBeQueried() {
        ToolResult result = store.search(call(Map.of("datasetVersion", "v0")));
        assertEquals("EMPTY", result.status());
        assertFalse(result.truncated());
        assertTrue(result.evidence().isEmpty());
    }

    @Test void versionPinningAndStableEvidenceSurviveImports() {
        seed();
        ToolResult before = store.search(call(Map.of()));
        store.ingest(new ImportBatch(List.of(event("new", 30, "alice"))));
        ToolResult old = store.search(call(Map.of()));
        assertEquals(before.evidence(), old.evidence());
        assertEquals(List.of("a", "b", "new", "z"), ids(store.search(call(Map.of("datasetVersion", "v2")))));
        assertEquals("v2", store.currentVersion());
        assertThrows(UnsupportedOperationException.class, () -> before.evidence().clear());
    }

    @Test void evidencePreservesJsonValuesProvenanceAndIntroductionVersion() throws Exception {
        store.ingest(new ImportBatch(List.of(event("quoted", 0, "a\"b\nc"))));
        store.ingest(new ImportBatch(List.of(event("new", 20, "alice"))));
        Evidence evidence = store.search(call(Map.of("datasetVersion", "v2", "eventId", "quoted"))).evidence().get(0);
        assertEquals("v2", evidence.datasetVersion());
        assertEquals("file:///sample.json#/quoted", evidence.provenance());
        Map<String,String> fields = new HashMap<>();
        try (var parser = new JsonFactory().createParser(evidence.payload())) {
            parser.nextToken();
            while (parser.nextToken() != com.fasterxml.jackson.core.JsonToken.END_OBJECT) {
                String field = parser.currentName(); parser.nextToken(); fields.put(field, parser.getValueAsString());
            }
        }
        assertEquals("a\"b\nc", fields.get("accountId"));
        assertNull(fields.get("hostId"));
        assertEquals("v1", fields.get("datasetVersion"));
        assertEquals(START.toString(), fields.get("timestamp"));
    }

    @Test void normalizesOffsetAndAcceptsSingleInstantInterval() {
        seed();
        assertEquals(List.of("a", "b"), ids(store.search(call(Map.of(
                "start", "2026-10-22T08:00:00-04:00", "end", START.toString())))));
    }

    @ParameterizedTest @ValueSource(strings={"0", "-1", "1001", "1.5", "2147483648", "all"})
    void rejectsInvalidLimits(String limit) {
        assertEquals(ToolException.Code.INVALID_ARGUMENT,
                assertThrows(ToolException.class, () -> store.search(call(Map.of("limit", limit)))).code());
    }

    @ParameterizedTest @ValueSource(strings={"2026-10-22T12:00:00", "not-time", "2026-02-30T12:00:00Z"})
    void rejectsInvalidTimes(String time) {
        assertThrows(ToolException.class, () -> store.search(call(Map.of("start", time))));
    }

    @Test void rejectsReversedTimesBlankUnknownAndMissingArguments() {
        assertThrows(ToolException.class, () -> store.search(call(Map.of("end", START.minusSeconds(1).toString()))));
        assertThrows(ToolException.class, () -> store.search(call(Map.of("accountId", " "))));
        assertThrows(ToolException.class, () -> store.search(call(Map.of("sql", "select all"))));
        var args = new HashMap<>(call(Map.of()).args()); args.remove("datasetVersion");
        assertThrows(ToolException.class, () -> store.search(new ToolCall(UUID.randomUUID(), "logSearch", args)));
    }

    @Test void unknownDatasetAndVersionHaveDistinctErrorsWithoutMutation() {
        seed();
        assertEquals(ToolException.Code.UNKNOWN_DATASET, assertThrows(ToolException.class,
                () -> store.search(call(Map.of("datasetId", "other")))).code());
        assertEquals(ToolException.Code.UNKNOWN_VERSION, assertThrows(ToolException.class,
                () -> store.search(call(Map.of("datasetVersion", "v99")))).code());
        assertEquals("v1", store.currentVersion());
        assertEquals(4, store.events("v1").size());
    }

    @Test void commandRejectsWrongToolAndNullCall() {
        var command = new LogSearchCommand(store);
        assertThrows(ToolException.class, () -> command.execute(null));
        assertThrows(ToolException.class, () -> command.execute(new ToolCall(UUID.randomUUID(), "userActivity", call(Map.of()).args())));
    }

    @Test void resultCopiesCallerListAndRejectsInvalidMetadata() {
        var input = new ArrayList<Evidence>();
        var result = new ToolResult("EMPTY", input, "v0", 0, false);
        input.add(new Evidence(UUID.randomUUID(), "e", "v0", "source", "{}"));
        assertTrue(result.evidence().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new ToolResult("EMPTY", List.of(), "v0", -1, false));
        assertThrows(IllegalArgumentException.class, () -> new ToolResult(" ", List.of(), "v0", 0, false));
    }
}
