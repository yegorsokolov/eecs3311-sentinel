package sentinel.data;

import com.fasterxml.jackson.core.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;
import sentinel.domain.IndicatorRecord;
import sentinel.tools.ToolException;

/** Bounded, strict local JSON loading. No store mutation occurs during parsing. */
final class IndicatorFeedLoader {
    static final int MAX_BYTES = 10 * 1024 * 1024;
    static final int MAX_RECORDS = 100_000;
    private static final Set<String> FIELDS = Set.of("indicator", "verdict", "source", "observedAt");
    private static final JsonFactory JSON = JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .streamReadConstraints(StreamReadConstraints.builder()
                    .maxNestingDepth(4).maxStringLength(65_536).maxNameLength(128).build()).build();

    record Entry(IndicatorRecord record, String provenance) { }
    record Feed(String version, Map<String, Entry> entries) {
        Feed { entries = Map.copyOf(entries); }
        Map<String, IndicatorRecord> content() {
            var result = new HashMap<String, IndicatorRecord>();
            entries.forEach((key, value) -> result.put(key, value.record()));
            return Map.copyOf(result);
        }
    }

    static Feed load(Path path, Instant now, int maxBytes, int maxRecords) {
        if (path == null) throw new ToolException(ToolException.Code.FEED_UNAVAILABLE, "Feed path is required.");
        Path source = path.toAbsolutePath().normalize();
        byte[] bytes;
        try {
            if (!Files.isRegularFile(source)) throw new IOException();
            if (Files.size(source) > maxBytes) throw invalid();
            try (var input = Files.newInputStream(source)) { bytes = input.readNBytes(maxBytes + 1); }
            if (bytes.length > maxBytes) throw invalid();
        } catch (IOException | SecurityException ex) {
            throw new ToolException(ToolException.Code.FEED_UNAVAILABLE, "Cannot read local indicator feed.");
        }
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
            if (text.startsWith("\uFEFF")) text = text.substring(1);
            var rows = new ArrayList<Map<String, String>>();
            String version = null;
            boolean recordsSeen = false;
            try (JsonParser parser = JSON.createParser(text)) {
                if (parser.nextToken() != JsonToken.START_OBJECT) throw invalid();
                while (parser.nextToken() != JsonToken.END_OBJECT) {
                    if (parser.currentToken() != JsonToken.FIELD_NAME) throw invalid();
                    String field = parser.currentName();
                    JsonToken token = parser.nextToken();
                    if ("feedVersion".equals(field)) {
                        if (token != JsonToken.VALUE_STRING || parser.getText().isBlank()) throw invalid();
                        version = parser.getText();
                    } else if ("records".equals(field)) {
                        if (token != JsonToken.START_ARRAY) throw invalid();
                        recordsSeen = true;
                        while (parser.nextToken() != JsonToken.END_ARRAY) {
                            if (rows.size() >= maxRecords || parser.currentToken() != JsonToken.START_OBJECT) throw invalid();
                            var row = new HashMap<String, String>();
                            while (parser.nextToken() != JsonToken.END_OBJECT) {
                                if (parser.currentToken() != JsonToken.FIELD_NAME || !FIELDS.contains(parser.currentName())) throw invalid();
                                String name = parser.currentName();
                                if (parser.nextToken() != JsonToken.VALUE_STRING || parser.getText().isBlank()) throw invalid();
                                row.put(name, parser.getText());
                            }
                            if (!row.keySet().equals(FIELDS)) throw invalid();
                            rows.add(row);
                        }
                    } else throw invalid();
                }
                if (version == null || !recordsSeen || parser.nextToken() != null) throw invalid();
            }
            var entries = new HashMap<String, Entry>();
            for (int i = 0; i < rows.size(); i++) {
                var row = rows.get(i);
                Instant observedAt = OffsetDateTime.parse(row.get("observedAt")).toInstant();
                if (observedAt.isAfter(now)) throw invalid();
                var record = new IndicatorRecord(row.get("indicator"), row.get("verdict"),
                        row.get("source"), observedAt, version);
                if (entries.putIfAbsent(record.indicator(), new Entry(record, source.toUri() + "#/records/" + i)) != null) {
                    throw invalid();
                }
            }
            return new Feed(version, entries);
        } catch (IOException | IllegalArgumentException | java.time.DateTimeException ex) {
            // Parser errors can contain input values; return a fixed safe diagnostic.
            throw invalid();
        }
    }

    private static ToolException invalid() {
        return new ToolException(ToolException.Code.INVALID_FEED,
                "Invalid indicator feed: check UTF-8 JSON, schema, limits, unique indicators and observation dates.");
    }
}
