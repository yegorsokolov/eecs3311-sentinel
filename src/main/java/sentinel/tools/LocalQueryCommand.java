package sentinel.tools;

import java.util.Objects;
import sentinel.data.DatasetStore;
import sentinel.domain.ToolCall;
import sentinel.domain.ToolResult;

/** Shared read-only retrieval workflow; executor supplies validated case scope later. */
public abstract class LocalQueryCommand implements ToolCommand {
    protected final DatasetStore store;
    protected LocalQueryCommand(DatasetStore store) { this.store = Objects.requireNonNull(store); }

    @Override
    public final ToolResult execute(ToolCall call) {
        if (call == null) throw new ToolException(ToolException.Code.INVALID_ARGUMENT, "Tool call is required.");
        long started = System.nanoTime();
        ToolResult result = select(call);
        return new ToolResult(result.status(), result.evidence(), result.sourceVersion(),
                Math.max(0, (System.nanoTime() - started) / 1_000_000), result.truncated(), result.activitySummary(), result.indicatorMatch());
    }

    protected abstract ToolResult select(ToolCall call);
}
