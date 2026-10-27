package sentinel.data;

import java.util.Set;
import sentinel.domain.Indicator;
import sentinel.domain.ToolCall;
import sentinel.tools.ToolException;

/** Shared validation for direct store calls and the command boundary. */
public record IndicatorLookupQuery(String indicator, String feedVersion) {
    public static IndicatorLookupQuery parse(ToolCall call) {
        if (call == null || !"indicatorLookup".equals(call.toolName())
                || !call.args().keySet().equals(Set.of("indicator", "feedVersion"))
                || call.args().get("feedVersion").isBlank()) {
            throw invalid();
        }
        try {
            return new IndicatorLookupQuery(Indicator.normalize(call.args().get("indicator")),
                    call.args().get("feedVersion"));
        } catch (IllegalArgumentException ex) { throw invalid(); }
    }

    private static ToolException invalid() {
        return new ToolException(ToolException.Code.INVALID_ARGUMENT,
                "indicatorLookup requires a valid indicator and nonblank feedVersion, with no extra arguments.");
    }
}
