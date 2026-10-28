package sentinel.data;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Set;
import sentinel.domain.ToolCall;
import sentinel.tools.ToolException;
import static sentinel.tools.ToolException.Code.INVALID_ARGUMENT;

/** Parsed search arguments; scope enforcement is shared by ToolExecutor sessions. */
public record LogSearchQuery(String datasetId, String version, Instant start, Instant end,
                      int limit, Map<String, String> filters) {
    static final int MAX_RESULTS = 1000;
    private static final Set<String> FILTERS = Set.of("eventId", "eventType", "accountId",
            "sourceIp", "hostId", "outcome");
    private static final Set<String> REQUIRED = Set.of("datasetId", "datasetVersion", "start", "end", "limit");

    public static LogSearchQuery parse(ToolCall call) {
        return parse(call, "logSearch", FILTERS, REQUIRED);
    }

    public static LogSearchQuery parseActivity(ToolCall call) {
        return parse(call, "userActivity", Set.of("accountId"),
                Set.of("datasetId", "datasetVersion", "start", "end", "limit", "accountId"));
    }

    private static LogSearchQuery parse(ToolCall call, String tool, Set<String> allowedFilters,
                                        Set<String> required) {
        if (call == null || !tool.equals(call.toolName())) throw invalid("Expected a " + tool + " call.");
        Map<String, String> args = call.args();
        if (!args.keySet().containsAll(required)) throw invalid("Missing required " + tool + " arguments.");
        for (var entry : args.entrySet()) {
            if (!required.contains(entry.getKey()) && !allowedFilters.contains(entry.getKey())) {
                throw invalid("Unknown " + tool + " argument.");
            }
            if (entry.getValue().isBlank()) throw invalid("Search arguments must not be blank.");
        }
        Instant start = time(args.get("start"));
        Instant end = time(args.get("end"));
        if (end.isBefore(start)) throw invalid("Search end must not precede start.");
        int limit;
        try {
            if (!args.get("limit").matches("[0-9]+")) throw new NumberFormatException();
            limit = Integer.parseInt(args.get("limit"));
            if (limit < 1 || limit > MAX_RESULTS) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            throw invalid("Search limit must be an integer from 1 to " + MAX_RESULTS + ".");
        }
        var filters = new java.util.HashMap<String, String>();
        for (String field : allowedFilters) if (args.containsKey(field)) filters.put(field, args.get(field));
        return new LogSearchQuery(args.get("datasetId"), args.get("datasetVersion"),
                start, end, limit, Map.copyOf(filters));
    }

    private static Instant time(String value) {
        try { return OffsetDateTime.parse(value).toInstant(); }
        catch (DateTimeParseException ex) { throw invalid("Search timestamps require an ISO-8601 UTC offset."); }
    }

    private static ToolException invalid(String message) {
        return new ToolException(INVALID_ARGUMENT, message);
    }
}
