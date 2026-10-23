package sentinel.tools;

import sentinel.data.DatasetStore;
import sentinel.domain.ToolCall;
import sentinel.domain.ToolResult;

/** Command adapter for versioned, filtered local event retrieval. */
public final class LogSearchCommand extends LocalQueryCommand {
    public LogSearchCommand(DatasetStore store) { super(store); }
    @Override
    protected ToolResult select(ToolCall call) { return store.search(call); }
}
