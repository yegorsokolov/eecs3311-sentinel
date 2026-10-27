package sentinel.data;

import com.fasterxml.jackson.core.JsonFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import sentinel.domain.*;
import sentinel.tools.*;
import static org.junit.jupiter.api.Assertions.*;

class IndicatorLookupTest {
    @TempDir Path temp;
    private static final Instant NOW = Instant.parse("2026-10-27T12:00:00Z");
    private final DatasetStore store = new DatasetStore("demo", Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofDays(30));

    private String row(String indicator, String observedAt) {
        return """
                {"indicator":"%s","verdict":"MALICIOUS","source":"synthetic feed","observedAt":"%s"}
                """.formatted(indicator, observedAt).strip();
    }
    private String feed(String version, String rows) {
        return "{\"feedVersion\":\"" + version + "\",\"records\":[" + rows + "]}";
    }
    private Path file(String text) throws Exception {
        Path path = temp.resolve(UUID.randomUUID() + ".json");
        return Files.writeString(path, text, StandardCharsets.UTF_8);
    }
    private void load(String text) throws Exception { store.loadIndicatorFeed(file(text)); }
    private ToolCall call(String indicator, String version) {
        return new ToolCall(UUID.randomUUID(), "indicatorLookup", Map.of("indicator", indicator, "feedVersion", version));
    }
    private ToolResult lookup(String indicator) {
        return new IndicatorLookupCommand(store).execute(call(indicator, "feed-1"));
    }
    private ToolException failure(ToolException.Code code, org.junit.jupiter.api.function.Executable action) {
        var ex = assertThrows(ToolException.class, action);
        assertEquals(code, ex.code());
        return ex;
    }

    @Test void normalizedMatchHasSourceDateVersionAndStableJsonEvidence() throws Exception {
        Path source = file(feed("feed-1", row("  Evil.Example. ", "2026-10-27T08:00:00-04:00")
                .replace("synthetic feed", "synthetic \\\"quoted\\\"")));
        assertEquals("feed-1", store.loadIndicatorFeed(source));
        var result = lookup("EVIL.EXAMPLE");
        assertEquals("SUCCESS", result.status());
        assertFalse(result.truncated());
        assertTrue(result.durationMillis() >= 0);
        assertEquals("feed-1", result.sourceVersion());
        assertNull(result.activitySummary());
        var match = result.indicatorMatch();
        assertEquals("evil.example", match.indicator());
        assertTrue(match.matched());
        assertFalse(match.stale());
        assertEquals("MALICIOUS", match.verdict());
        assertEquals("synthetic \"quoted\"", match.source());
        assertEquals(NOW, match.observedAt());
        assertEquals("feed-1", match.feedVersion());
        Evidence evidence = result.evidence().get(0);
        assertEquals(source.toUri() + "#/records/0", evidence.provenance());
        assertEquals("evil.example", evidence.sourceEventId());
        assertEquals("feed-1", evidence.datasetVersion());
        var fields = new HashMap<String, String>();
        try (var parser = new JsonFactory().createParser(evidence.payload())) {
            parser.nextToken();
            while (parser.nextToken() != com.fasterxml.jackson.core.JsonToken.END_OBJECT) {
                String name = parser.currentName(); parser.nextToken(); fields.put(name, parser.getValueAsString());
            }
        }
        assertEquals(match.source(), fields.get("source"));
        assertEquals(NOW.toString(), fields.get("observedAt"));
        assertEquals("MALICIOUS", fields.get("verdict"));
        assertEquals(evidence, lookup("evil.example.").evidence().get(0));
        assertThrows(UnsupportedOperationException.class, () -> result.evidence().clear());
        assertEquals("v0", store.currentVersion());
        assertTrue(store.events("v0").isEmpty());
    }

    @Test void supportsIpv4AndCanonicalIpv6WithoutHostnameResolution() throws Exception {
        load(feed("feed-1", row("192.0.2.10", NOW.toString()) + "," + row("2001:db8::1", NOW.toString())));
        assertTrue(lookup("192.0.2.10").indicatorMatch().matched());
        assertTrue(lookup("2001:0DB8:0:0:0:0:0:1").indicatorMatch().matched());
        assertFalse(lookup("192.0.2.11").indicatorMatch().matched());
    }

    @Test void noMatchIsUnknownWithoutInventedEvidenceOrSource() throws Exception {
        load(feed("feed-1", ""));
        var result = lookup("missing.example");
        assertEquals("UNKNOWN", result.status());
        assertTrue(result.evidence().isEmpty());
        assertFalse(result.indicatorMatch().matched());
        assertEquals("UNKNOWN", result.indicatorMatch().verdict());
        assertNull(result.indicatorMatch().source());
        assertNull(result.indicatorMatch().observedAt());
        assertFalse(result.indicatorMatch().stale());
    }

    @Test void matchedUnknownVerdictIsDistinctFromNoMatch() throws Exception {
        load(feed("feed-1", row("unknown.example", NOW.toString()).replace("MALICIOUS", "UNKNOWN")));
        var result = lookup("unknown.example");
        assertEquals("SUCCESS", result.status());
        assertTrue(result.indicatorMatch().matched());
        assertEquals("UNKNOWN", result.indicatorMatch().verdict());
        assertEquals(1, result.evidence().size());
    }

    @Test void staleRetainsVerdictAndDateAndExactThresholdIsFresh() throws Exception {
        Instant boundary = NOW.minus(Duration.ofDays(30));
        load(feed("feed-1", row("old.example", boundary.minusNanos(1).toString())
                .replace("MALICIOUS", "BENIGN") + "," + row("edge.example", boundary.toString())));
        var stale = lookup("old.example");
        assertEquals("STALE", stale.status());
        assertTrue(stale.indicatorMatch().stale());
        assertEquals("BENIGN", stale.indicatorMatch().verdict());
        assertEquals(boundary.minusNanos(1), stale.indicatorMatch().observedAt());
        assertEquals(1, stale.evidence().size());
        assertEquals("SUCCESS", lookup("edge.example").status());
    }

    @Test void unloadedAndMissingVersionsFailExplicitly() throws Exception {
        failure(ToolException.Code.FEED_UNAVAILABLE, () -> lookup("evil.example"));
        load(feed("feed-1", ""));
        failure(ToolException.Code.UNKNOWN_FEED_VERSION, () -> store.lookup(call("evil.example", "feed-2")));
        failure(ToolException.Code.FEED_UNAVAILABLE, () -> store.loadIndicatorFeed(temp.resolve("missing.json")));
        failure(ToolException.Code.FEED_UNAVAILABLE, () -> store.loadIndicatorFeed(temp));
        failure(ToolException.Code.FEED_UNAVAILABLE, () -> store.loadIndicatorFeed(null));
    }

    @Test void versionsRemainPinnedAndSameVersionCannotBeReplaced() throws Exception {
        String original = feed("feed-1", row("evil.example", NOW.toString()));
        Path first = file(original);
        store.loadIndicatorFeed(first);
        var before = lookup("evil.example");
        load(original); // Idempotent reload from another path preserves original provenance.
        assertEquals(before.evidence(), lookup("evil.example").evidence());
        load(feed("feed-2", row("evil.example", NOW.toString()).replace("MALICIOUS", "BENIGN")));
        assertEquals("BENIGN", store.lookup(call("evil.example", "feed-2")).indicatorMatch().verdict());
        assertNotEquals(before.evidence().get(0).id(),
                store.lookup(call("evil.example", "feed-2")).evidence().get(0).id());
        failure(ToolException.Code.INVALID_FEED,
                () -> load(original.replace("MALICIOUS", "BENIGN")));
        assertEquals(before.evidence(), lookup("evil.example").evidence());
        assertEquals("MALICIOUS", lookup("evil.example").indicatorMatch().verdict());
    }

    @Test void invalidLoadIsAtomicAndKeepsEarlierVersions() throws Exception {
        load(feed("feed-1", row("evil.example", NOW.toString())));
        failure(ToolException.Code.INVALID_FEED,
                () -> load(feed("bad", row("new.example", NOW.toString()) + ",{}")));
        failure(ToolException.Code.UNKNOWN_FEED_VERSION, () -> store.lookup(call("new.example", "bad")));
        assertEquals("SUCCESS", lookup("evil.example").status());
        failure(ToolException.Code.FEED_UNAVAILABLE, () -> store.loadIndicatorFeed(temp.resolve("missing")));
        assertEquals("SUCCESS", lookup("evil.example").status());
    }

    @ParameterizedTest @ValueSource(strings={"", " ", "localhost", "https://evil.example/path", "evil..example",
            "-evil.example", "evil-.example", "*.example", "bad_name.example", "999.2.3.4", "01.2.3.4",
            "127.1", "1.2.3.4.", "2001:db8::zz", "[2001:db8::1]", "fe80::1%eth0", "evil.example:443"})
    void rejectsInvalidIndicators(String indicator) {
        failure(ToolException.Code.INVALID_ARGUMENT, () -> lookup(indicator));
        failure(ToolException.Code.INVALID_ARGUMENT, () -> store.lookup(call(indicator, "feed-1")));
    }

    @Test void rejectsWrongToolMissingExtraAndBlankArguments() {
        failure(ToolException.Code.INVALID_ARGUMENT, () -> new IndicatorLookupCommand(store).execute(null));
        failure(ToolException.Code.INVALID_ARGUMENT, () -> store.lookup(null));
        failure(ToolException.Code.INVALID_ARGUMENT, () -> store.lookup(new ToolCall(UUID.randomUUID(), "logSearch",
                call("evil.example", "feed-1").args())));
        for (var args : List.of(Map.of("indicator", "evil.example"), Map.of("feedVersion", "feed-1"),
                Map.of("indicator", "evil.example", "feedVersion", " "),
                Map.of("indicator", "evil.example", "feedVersion", "feed-1", "extra", "x"))) {
            failure(ToolException.Code.INVALID_ARGUMENT,
                    () -> new IndicatorLookupCommand(store).execute(new ToolCall(UUID.randomUUID(), "indicatorLookup", args)));
        }
    }

    @ParameterizedTest @ValueSource(strings={"[]", "{}", "null", "{\"feedVersion\":\"x\"}",
            "{\"feedVersion\":\" \",\"records\":[]}", "{\"feedVersion\":\"x\",\"records\":null}",
            "{\"feedVersion\":\"x\",\"records\":[null]}", "{\"feedVersion\":\"x\",\"records\":[],\"extra\":1}",
            "{\"feedVersion\":\"x\",\"feedVersion\":\"x\",\"records\":[]}",
            "{\"feedVersion\":\"x\",\"records\":[]} []", "{\"feedVersion\":\"x\",\"records\":[",
            "{\"feedVersion\":\"x\",\"records\":[{}]}"})
    void rejectsMalformedSchema(String json) throws Exception {
        failure(ToolException.Code.INVALID_FEED, () -> load(json));
    }

    @Test void rejectsInvalidFieldsDatesDuplicatesAndOversizedNames() throws Exception {
        String valid = row("evil.example", NOW.toString());
        for (String invalid : List.of(valid.replace("MALICIOUS", "SAFE"),
                valid.replace("\"source\":\"synthetic feed\"", "\"source\":null"),
                valid.replace("\"source\":\"synthetic feed\"", "\"source\":3"),
                valid.replace("\"source\":\"synthetic feed\"", "\"source\":\" \""),
                valid.replace("\"source\":", "\"extra\":\"x\",\"source\":"),
                valid.replace("\"source\":", "\"source\":\"x\",\"source\":"),
                row("evil.example", "2026-02-30T00:00:00Z"),
                row("evil.example", "2026-10-27T12:00:00"),
                row("evil.example", NOW.plusSeconds(1).toString()),
                row("bad..example", NOW.toString()),
                row("a".repeat(64) + ".example", NOW.toString()))) {
            failure(ToolException.Code.INVALID_FEED, () -> load(feed("x", invalid)));
        }
        failure(ToolException.Code.INVALID_FEED,
                () -> load(feed("x", valid + "," + row("EVIL.EXAMPLE.", NOW.toString()))));
    }

    @Test void enforcesByteAndRecordLimitsAndStrictUtf8() throws Exception {
        String one = feed("x", row("evil.example", NOW.toString()));
        Path path = file(one);
        int bytes = one.getBytes(StandardCharsets.UTF_8).length;
        assertEquals("x", IndicatorFeedLoader.load(path, NOW, bytes, 1).version());
        failure(ToolException.Code.INVALID_FEED, () -> IndicatorFeedLoader.load(path, NOW, bytes - 1, 1));
        Path two = file(feed("x", row("a.example", NOW.toString()) + "," + row("b.example", NOW.toString())));
        failure(ToolException.Code.INVALID_FEED, () -> IndicatorFeedLoader.load(two, NOW, 4096, 1));
        Path bad = Files.write(temp.resolve("bad-utf8.json"), new byte[]{(byte) 0xc3, (byte) 0x28});
        failure(ToolException.Code.INVALID_FEED, () -> store.loadIndicatorFeed(bad));
        assertEquals("x", IndicatorFeedLoader.load(file("\uFEFF" + one), NOW, 4096, 1).version());
        assertEquals("x", IndicatorFeedLoader.load(file("{\"records\":[],\"feedVersion\":\"x\"}"), NOW, 4096, 1).version());
    }

    @Test void freshnessUsesInjectedClockAndPolicy() throws Exception {
        var clock = new Clock() {
            private Instant instant = NOW;
            @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { return instant; }
            void advance() { instant = instant.plusSeconds(2); }
        };
        var timed = new DatasetStore("demo", clock, Duration.ofSeconds(1));
        timed.loadIndicatorFeed(file(feed("x", row("evil.example", NOW.toString()))));
        var first = timed.lookup(call("evil.example", "x"));
        clock.advance();
        var later = timed.lookup(call("evil.example", "x"));
        assertEquals("SUCCESS", first.status());
        assertEquals("STALE", later.status());
        assertEquals(first.evidence(), later.evidence());
        assertThrows(IllegalArgumentException.class, () -> new DatasetStore("demo", clock, Duration.ZERO));
    }
}
