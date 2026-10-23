package sentinel.data;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import sentinel.domain.Event;
import sentinel.domain.ImportBatch;
import sentinel.domain.Summary;

/** Bounded UTF-8 JSON validation and UTC normalization before atomic ingestion. */
public final class LogImporter {
    public static final int DEFAULT_MAX_BYTES = 10 * 1024 * 1024;
    public static final int DEFAULT_MAX_EVENTS = 100_000;
    private static final Set<String> FIELDS = Set.of(
            "eventId", "timestamp", "eventType", "accountId", "sourceIp", "hostId", "outcome");
    private static final JsonFactory JSON = JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .streamReadConstraints(StreamReadConstraints.builder()
                    .maxNestingDepth(4).maxStringLength(65_536).maxNameLength(128).build())
            .build();
    private final DatasetStore store;
    private final int maxBytes;
    private final int maxEvents;

    public LogImporter(DatasetStore store) {
        this(store, DEFAULT_MAX_BYTES, DEFAULT_MAX_EVENTS);
    }

    /** Explicit bounds allow small, deterministic boundary tests. */
    public LogImporter(DatasetStore store, int maxBytes, int maxEvents) {
        this.store = Objects.requireNonNull(store, "store");
        if (maxBytes < 1 || maxBytes == Integer.MAX_VALUE || maxEvents < 1) {
            throw new IllegalArgumentException("Import limits must be positive and byte limit must allow an overflow probe.");
        }
        this.maxBytes = maxBytes;
        this.maxEvents = maxEvents;
    }

    public Summary importFile(Path path) {
        Objects.requireNonNull(path, "path");
        Path source = path.toAbsolutePath().normalize();
        try {
            if (!Files.isRegularFile(source)) return failure("Import source must be a readable regular file.");
            if (Files.size(source) > maxBytes) return failure("Import exceeds the byte limit.");
            byte[] bytes;
            try (var input = Files.newInputStream(source)) {
                // The second bound handles a file that grows after Files.size().
                bytes = input.readNBytes(maxBytes + 1);
            }
            if (bytes.length > maxBytes) return failure("Import exceeds the byte limit.");
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
            if (text.startsWith("\uFEFF")) text = text.substring(1);
            List<Event> staged = new ArrayList<>();
            try (JsonParser parser = JSON.createParser(text)) {
                if (parser.nextToken() != JsonToken.START_ARRAY) {
                    return failure("Import must contain one JSON array of event objects.");
                }
                while (parser.nextToken() != JsonToken.END_ARRAY) {
                    if (staged.size() >= maxEvents) return failure("Import exceeds the event-count limit.");
                    staged.add(readEvent(parser, source, staged.size()));
                }
                if (parser.nextToken() != null) return failure("Unexpected content after the event array.");
            }
            return store.ingest(new ImportBatch(staged));
        } catch (ValidationException ex) {
            return failure(ex.getMessage());
        } catch (JsonProcessingException ex) {
            // Jackson's full error can include source values; do not echo those.
            return failure("Malformed JSON or duplicate object fields; import rejected.");
        } catch (IOException | SecurityException ex) {
            return failure("Cannot read import file as UTF-8; check encoding and file access.");
        }
    }

    private static Event readEvent(JsonParser parser, Path source, int index) throws IOException {
        if (parser.currentToken() != JsonToken.START_OBJECT) {
            throw invalid(index, "expected an event object");
        }
        Map<String, String> values = new HashMap<>();
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            if (parser.currentToken() != JsonToken.FIELD_NAME) throw invalid(index, "expected a field name");
            String field = parser.currentName();
            if (!FIELDS.contains(field)) throw invalid(index, "unknown field; see the event import contract");
            JsonToken token = parser.nextToken();
            if (token == JsonToken.VALUE_STRING) values.put(field, parser.getText());
            else if (token == JsonToken.VALUE_NULL) values.put(field, null);
            else throw invalid(index, "fields must be strings or null");
        }
        String eventId = required(values, "eventId", index);
        String timestamp = required(values, "timestamp", index);
        String type = required(values, "eventType", index);
        java.time.Instant instant;
        try {
            instant = OffsetDateTime.parse(timestamp).toInstant();
        } catch (DateTimeParseException ex) {
            throw invalid(index, "timestamp must be ISO-8601 with Z or an explicit UTC offset");
        }
        return new Event(eventId, instant, type, optional(values, "accountId"), optional(values, "sourceIp"),
                optional(values, "hostId"), optional(values, "outcome"),
                source.toUri() + "#/" + index, "pending");
    }

    private static String required(Map<String, String> values, String field, int index) {
        String value = values.get(field);
        if (value == null || value.isBlank()) throw invalid(index, field + " must be a nonblank string");
        return value;
    }

    private static String optional(Map<String, String> values, String field) {
        String value = values.get(field);
        return value == null || value.isBlank() ? null : value;
    }

    private Summary failure(String message) {
        return new Summary(store.currentVersion(), 0, 0, List.of(message));
    }

    private static ValidationException invalid(int index, String message) {
        return new ValidationException("Event at index " + index + ": " + message + ".");
    }

    private static final class ValidationException extends IllegalArgumentException {
        private ValidationException(String message) { super(message); }
    }
}
