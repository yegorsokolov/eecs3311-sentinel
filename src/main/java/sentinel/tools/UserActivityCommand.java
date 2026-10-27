package sentinel.tools;

import sentinel.domain.ToolCall;
import sentinel.domain.ToolResult;
import sentinel.data.DatasetStore;

/** Read-only account activity retrieval against a pinned dataset version. */
public final class UserActivityCommand extends LocalQueryCommand {
    public UserActivityCommand(DatasetStore store) { super(store); }
    @Override
    protected ToolResult select(ToolCall call) { return store.activity(call); }
}
