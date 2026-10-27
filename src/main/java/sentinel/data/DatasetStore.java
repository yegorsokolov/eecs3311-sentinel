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
 * JSON disk persistence and active-case integration will be implemented separately.
 */
public final class DatasetStore {
    private record State(int revision, Map<String, Event> byId, Map<String, List<Event>> versions) { }
    private final String datasetId;
    private final java.time.Clock clock;
    private final java.time.Duration maxIndicatorAge;
    private final java.util.concurrent.ConcurrentMap<String, IndicatorFeedLoader.Feed> feeds =
            new java.util.concurrent.ConcurrentHashMap<>();
    private volatile State state = new State(0, Map.of(), Map.of("v0", List.of()));

    public DatasetStore() {
        this("local");
    }

    public DatasetStore(String datasetId) {
        this(datasetId, java.time.Clock.systemUTC(), java.time.Duration.ofDays(30));
    }

    /** Explicit freshness policy/clock make local feed evaluation reproducible. */
    public DatasetStore(String datasetId, java.time.Clock clock, java.time.Duration maxIndicatorAge) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.maxIndicatorAge = Objects.requireNonNull(maxIndicatorAge, "maxIndicatorAge");
        if (maxIndicatorAge.isNegative() || maxIndicatorAge.isZero()) {
            throw new IllegalArgumentException("Indicator freshness duration must be positive.");
        }
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
        long started = System.nanoTime();
        LogSearchQuery query = LogSearchQuery.parse(call);
        if (!datasetId.equals(query.datasetId())) {
            throw new sentinel.tools.ToolException(sentinel.tools.ToolException.Code.UNKNOWN_DATASET,
                    "Unknown dataset.");
        }
        List<Event> view = state.versions().get(query.version());
        if (view == null) {
            throw new sentinel.tools.ToolException(sentinel.tools.ToolException.Code.UNKNOWN_VERSION,
                    "Unknown dataset version.");
        }
        // A captured immutable version remains stable even during a concurrent import.
        List<Event> matches = view.stream()
                .filter(e -> !e.timestamp().isBefore(query.start()) && !e.timestamp().isAfter(query.end()))
                .filter(e -> matches(e, query.filters()))
                .sorted(java.util.Comparator.comparing(Event::timestamp).thenComparing(Event::eventId))
                .limit((long) query.limit() + 1).toList();
        boolean truncated = matches.size() > query.limit();
        List<sentinel.domain.Evidence> evidence = matches.stream().limit(query.limit())
                .map(e -> EventEvidence.from(datasetId, query.version(), e)).toList();
        String status = truncated ? "PARTIAL" : evidence.isEmpty() ? "EMPTY" : "SUCCESS";
        return new ToolResult(status, evidence, query.version(),
                Math.max(0, (System.nanoTime() - started) / 1_000_000), truncated);
    }

    private static boolean matches(Event event, Map<String, String> filters) {
        for (var filter : filters.entrySet()) {
            String value = switch (filter.getKey()) {
                case "eventId" -> event.eventId();
                case "eventType" -> event.eventType();
                case "accountId" -> event.accountId();
                case "sourceIp" -> event.sourceIp();
                case "hostId" -> event.hostId();
                case "outcome" -> event.outcome();
                default -> throw new IllegalStateException("Unvalidated search filter.");
            };
            if (!filter.getValue().equals(value)) return false;
        }
        return true;
    }

    /** Parse fully before atomic publication. An existing feed version can never change content. */
    public String loadIndicatorFeed(java.nio.file.Path path) {
        var feed = IndicatorFeedLoader.load(path, clock.instant(),
                IndicatorFeedLoader.MAX_BYTES, IndicatorFeedLoader.MAX_RECORDS);
        var previous = feeds.putIfAbsent(feed.version(), feed);
        if (previous != null && !previous.content().equals(feed.content())) {
            throw new sentinel.tools.ToolException(sentinel.tools.ToolException.Code.INVALID_FEED,
                    "Feed version already exists with different content.");
        }
        return feed.version();
    }

    public ToolResult lookup(ToolCall call) {
        long started = System.nanoTime();
        var query = IndicatorLookupQuery.parse(call);
        var feed = feeds.get(query.feedVersion());
        if (feed == null) {
            var code = feeds.isEmpty() ? sentinel.tools.ToolException.Code.FEED_UNAVAILABLE
                    : sentinel.tools.ToolException.Code.UNKNOWN_FEED_VERSION;
            throw new sentinel.tools.ToolException(code, "Requested indicator feed version is not loaded.");
        }
        var entry = feed.entries().get(query.indicator());
        boolean stale = entry != null && java.time.Duration.between(entry.record().observedAt(), clock.instant())
                .compareTo(maxIndicatorAge) > 0;
        var match = entry == null
                ? new sentinel.domain.IndicatorMatch(query.indicator(), query.feedVersion(), false,
                        "UNKNOWN", null, null, false)
                : new sentinel.domain.IndicatorMatch(query.indicator(), query.feedVersion(), true,
                        entry.record().verdict(), entry.record().source(), entry.record().observedAt(), stale);
        return new ToolResult(entry == null ? "UNKNOWN" : stale ? "STALE" : "SUCCESS",
                entry == null ? List.of() : List.of(IndicatorEvidence.from(entry)), feed.version(),
                Math.max(0, (System.nanoTime() - started) / 1_000_000), false, null, match);
    }

    public ToolResult activity(ToolCall call) {
        long started = System.nanoTime();
        LogSearchQuery query = LogSearchQuery.parseActivity(call);
        if (!datasetId.equals(query.datasetId())) {
            throw new sentinel.tools.ToolException(sentinel.tools.ToolException.Code.UNKNOWN_DATASET,
                    "Unknown dataset.");
        }
        List<Event> view = state.versions().get(query.version());
        if (view == null) {
            throw new sentinel.tools.ToolException(sentinel.tools.ToolException.Code.UNKNOWN_VERSION,
                    "Unknown dataset version.");
        }
        List<Event> matches = view.stream()
                .filter(e -> !e.timestamp().isBefore(query.start()) && !e.timestamp().isAfter(query.end()))
                .filter(e -> query.filters().get("accountId").equals(e.accountId()))
                .sorted(java.util.Comparator.comparing(Event::timestamp).thenComparing(Event::eventId)).toList();
        int successes = 0, failures = 0, unknownHosts = 0;
        var hosts = new java.util.HashSet<String>();
        for (Event event : matches) {
            if ("success".equals(event.outcome())) successes++;
            else if ("failure".equals(event.outcome())) failures++;
            if (event.hostId() == null) unknownHosts++;
            else hosts.add(event.hostId());
        }
        List<sentinel.domain.Evidence> evidence = matches.stream().limit(query.limit())
                .map(e -> EventEvidence.from(datasetId, query.version(), e)).toList();
        boolean truncated = matches.size() > query.limit();
        var summary = new sentinel.domain.ActivitySummary(query.filters().get("accountId"),
                query.start(), query.end(), matches.size(), evidence.size(), successes, failures,
                matches.size() - successes - failures, hosts.size(), unknownHosts,
                matches.isEmpty() ? null : matches.get(0).timestamp(),
                matches.isEmpty() ? null : matches.get(matches.size() - 1).timestamp());
        return new ToolResult(truncated ? "PARTIAL" : matches.isEmpty() ? "EMPTY" : "SUCCESS",
                evidence, query.version(), Math.max(0, (System.nanoTime() - started) / 1_000_000),
                truncated, summary);
    }
}
