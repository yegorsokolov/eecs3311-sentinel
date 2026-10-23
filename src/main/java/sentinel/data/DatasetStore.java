package sentinel.data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import sentinel.domain.Event;
import sentinel.domain.ImportBatch;
import sentinel.domain.Summary;
import sentinel.domain.ToolCall;
import sentinel.domain.ToolResult;

/**
 * One in-memory dataset with immutable retained versions.
 * A single state replacement publishes a fully checked batch atomically.
 * JSON disk persistence and query tools will be implemented separately.
 */
public final class DatasetStore {
    private record State(int revision, Map<String, Event> byId, Map<String, List<Event>> versions) { }
    private final String datasetId;
    private volatile State state = new State(0, Map.of(), Map.of("v0", List.of()));

    public DatasetStore() {
        this("local");
    }

    public DatasetStore(String datasetId) {
        if (datasetId == null || datasetId.isBlank()) {
            throw new IllegalArgumentException("datasetId must not be blank.");
        }
        this.datasetId = datasetId;
    }

    public String datasetId() {
        return datasetId;
    }

    public String currentVersion() {
        return "v" + state.revision();
    }

    /** Stable read view; older versions remain available after further imports. */
    public List<Event> events(String version) {
        List<Event> events = state.versions().get(version);
        if (events == null) throw new IllegalArgumentException("Unknown dataset version.");
        return events;
    }

    public synchronized Summary ingest(ImportBatch batch) {
        Objects.requireNonNull(batch, "batch");
        State before = state;
        String previousVersion = "v" + before.revision();
        String nextVersion = "v" + Math.incrementExact(before.revision());
        Map<String, Event> staged = new LinkedHashMap<>(before.byId());
        int accepted = 0;
        int duplicates = 0;
        for (Event event : batch.events()) {
            Event existing = staged.get(event.eventId());
            if (existing != null) {
                if (!sameContent(existing, event)) {
                    return new Summary(previousVersion, 0, 0,
                            List.of("Conflicting event ID; the entire import was rejected."));
                }
                duplicates++;
            } else {
                staged.put(event.eventId(), new Event(event.eventId(), event.timestamp(), event.eventType(),
                        event.accountId(), event.sourceIp(), event.hostId(), event.outcome(),
                        event.sourcePosition(), nextVersion));
                accepted++;
            }
        }
        if (accepted == 0) return new Summary(previousVersion, 0, duplicates, List.of());
        // Preserve deterministic insertion order; Map.copyOf alone does not promise it.
        Map<String, Event> immutableEvents = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(staged));
        Map<String, List<Event>> versions = new LinkedHashMap<>(before.versions());
        versions.put(nextVersion, List.copyOf(staged.values()));
        state = new State(before.revision() + 1, immutableEvents, Map.copyOf(versions));
        return new Summary(nextVersion, accepted, duplicates, List.of());
    }

    private static boolean sameContent(Event a, Event b) {
        // Source location and introduction version are provenance, not event identity.
        return a.eventId().equals(b.eventId()) && a.timestamp().equals(b.timestamp())
                && a.eventType().equals(b.eventType())
                && Objects.equals(a.accountId(), b.accountId())
                && Objects.equals(a.sourceIp(), b.sourceIp())
                && Objects.equals(a.hostId(), b.hostId())
                && Objects.equals(a.outcome(), b.outcome());
    }

    public ToolResult search(ToolCall call) {
        throw new UnsupportedOperationException("Log search is implemented in the next retrieval step.");
    }

    public ToolResult lookup(ToolCall call) {
        throw new UnsupportedOperationException("Indicator lookup is implemented in the retrieval step.");
    }

    public ToolResult activity(ToolCall call) {
        throw new UnsupportedOperationException("Account activity is implemented in the retrieval step.");
    }
}
