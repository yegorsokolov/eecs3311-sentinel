package sentinel.data;

import java.util.*;
import sentinel.domain.*;
import sentinel.tools.ToolException;

/** Source and query checks before an application attaches command output to a case. */
public final class ResultVerifier {
    private ResultVerifier() { }

    public static void validate(DatasetStore store, CaseSnapshot snapshot, ToolCall call, ToolResult result) {
        if (result == null) throw invalid();
        if ("indicatorLookup".equals(call.toolName())) {
            var query = IndicatorLookupQuery.parse(call);
            var match = result.indicatorMatch();
            if (!query.feedVersion().equals(result.sourceVersion()) || result.truncated()
                    || result.activitySummary() != null || match == null
                    || !query.indicator().equals(match.indicator()) || !query.feedVersion().equals(match.feedVersion())) throw invalid();
            if (!match.matched()) {
                if (!"UNKNOWN".equals(result.status()) || !result.evidence().isEmpty()) throw invalid();
            } else {
                if (result.evidence().size() != 1 || !(match.stale() ? "STALE" : "SUCCESS").equals(result.status())) throw invalid();
                var record = store.resolveIndicator(result.evidence().get(0)).orElseThrow(ResultVerifier::invalid);
                if (!record.indicator().equals(match.indicator()) || !record.feedVersion().equals(match.feedVersion())
                        || !record.verdict().equals(match.verdict()) || !record.source().equals(match.source())
                        || !record.observedAt().equals(match.observedAt())) throw invalid();
            }
            return;
        }
        var query = "logSearch".equals(call.toolName()) ? LogSearchQuery.parse(call) : LogSearchQuery.parseActivity(call);
        if (!query.version().equals(result.sourceVersion()) || result.evidence().size() > query.limit()
                || result.indicatorMatch() != null) throw invalid();
        String expected = result.truncated() ? "PARTIAL" : result.evidence().isEmpty() ? "EMPTY" : "SUCCESS";
        if (!expected.equals(result.status())) throw invalid();
        var ids = new HashSet<UUID>();
        Event previous = null;
        for (Evidence item : result.evidence()) {
            if (!ids.add(item.id()) || !query.version().equals(item.datasetVersion())) throw invalid();
            Event event = store.resolveEvent(item).orElseThrow(ResultVerifier::invalid);
            if (!CaseEvidence.inScope(event, snapshot) || event.timestamp().isBefore(query.start())
                    || event.timestamp().isAfter(query.end()) || !matches(event, query.filters())) throw invalid();
            if (previous != null && Comparator.comparing(Event::timestamp).thenComparing(Event::eventId).compare(previous, event) > 0) throw invalid();
            previous = event;
        }
        var summary = result.activitySummary();
        if ("userActivity".equals(call.toolName())) {
            if (summary == null || !query.filters().get("accountId").equals(summary.accountId())
                    || !query.start().equals(summary.start()) || !query.end().equals(summary.end())
                    || summary.returnedEvents() != result.evidence().size()
                    || (summary.totalEvents() > summary.returnedEvents()) != result.truncated()) throw invalid();
        } else if (summary != null) throw invalid();
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
                default -> null;
            };
            if (!filter.getValue().equals(value)) return false;
        }
        return true;
    }

    private static ToolException invalid() {
        return new ToolException(ToolException.Code.INVALID_RESULT, "Tool output failed source or query validation.");
    }
}
