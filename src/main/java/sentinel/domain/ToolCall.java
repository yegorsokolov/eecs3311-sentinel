package sentinel.domain;

import java.util.Map;
import java.util.UUID;

/** Immutable tool request. Registry/argument validation belongs to PlanValidator/ToolExecutor. */
public record ToolCall(UUID id, String toolName, Map<String, String> args) {
    public ToolCall {
        DomainValidation.required(id, "id");
        DomainValidation.requiredText(toolName, "toolName");
        args = Map.copyOf(DomainValidation.required(args, "args"));
    }

    public String key() {
        // Validate tool arguments before using this key. Exact-match filters keep their original text.
        var normalized = new java.util.TreeMap<>(args);
        if ("logSearch".equals(toolName) || "userActivity".equals(toolName)) {
            for (String field : java.util.List.of("start", "end")) {
                if (normalized.containsKey(field)) {
                    normalized.put(field, java.time.OffsetDateTime.parse(normalized.get(field)).toInstant().toString());
                }
            }
            if (normalized.containsKey("limit")) {
                normalized.put("limit", Integer.toString(Integer.parseInt(normalized.get("limit"))));
            }
        } else if ("indicatorLookup".equals(toolName) && normalized.containsKey("indicator")) {
            normalized.put("indicator", Indicator.normalize(normalized.get("indicator")));
        }
        // Length-prefixed fields avoid delimiter collisions; call UUID and argument order are irrelevant.
        var key = new StringBuilder();
        append(key, toolName);
        normalized.forEach((name, value) -> { append(key, name); append(key, value); });
        return key.toString();
    }

    private static void append(StringBuilder key, String value) {
        key.append(value.length()).append(':').append(value);
    }
}
