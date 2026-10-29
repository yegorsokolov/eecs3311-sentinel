package sentinel.data;

import java.util.*;
import sentinel.domain.*;
import sentinel.tools.ToolScope;

/** Resolve accepted evidence against actual immutable local sources before authorizing enrichment. */
public final class CaseEvidence {
    private CaseEvidence() { }

    public static List<Event> events(DatasetStore store, CaseSnapshot snapshot) {
        if (snapshot.scope() == null || !store.datasetId().equals(snapshot.scope().datasetId())) return List.of();
        Set<Event> current = Set.copyOf(store.events(snapshot.scope().datasetVersion()));
        var unique = new LinkedHashMap<String, Event>();
        for (Evidence item : snapshot.evidence()) {
            store.resolveEvent(item).filter(current::contains).filter(event -> inScope(event, snapshot))
                    .ifPresent(event -> unique.putIfAbsent(event.eventId(), event));
        }
        return unique.values().stream().sorted(Comparator.comparing(Event::timestamp).thenComparing(Event::eventId)).toList();
    }

    public static Set<String> indicators(DatasetStore store, CaseSnapshot snapshot) {
        var indicators = new HashSet<String>();
        for (Event event : events(store, snapshot)) {
            if (event.sourceIp() != null) {
                try { indicators.add(Indicator.normalize(event.sourceIp())); }
                catch (IllegalArgumentException ignored) { /* Optional imported text is not necessarily an indicator. */ }
            }
        }
        return Set.copyOf(indicators);
    }

    public static ToolScope scope(DatasetStore store, CaseSnapshot snapshot) {
        if (snapshot.scope() == null) throw new IllegalStateException("Case scope requires clarification.");
        return new ToolScope(snapshot.scope(), ToolScope.TargetKind.valueOf(snapshot.targetType().name()),
                snapshot.feedVersion(), indicators(store, snapshot));
    }

    public static boolean inScope(Event event, CaseSnapshot snapshot) {
        CaseScope scope = snapshot.scope();
        if (scope == null || event.timestamp().isBefore(scope.start()) || event.timestamp().isAfter(scope.end())) return false;
        if (snapshot.targetType() == TargetType.ACCOUNT) return scope.target().equals(event.accountId());
        try { return Indicator.normalize(scope.target()).equals(Indicator.normalize(event.sourceIp())); }
        catch (IllegalArgumentException ex) { return false; }
    }
}
