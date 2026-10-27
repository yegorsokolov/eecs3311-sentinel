package sentinel.tools;

import sentinel.data.DatasetStore;
import sentinel.data.IndicatorLookupQuery;
import sentinel.domain.ToolCall;
import sentinel.domain.ToolResult;

/** Read-only lookup; invalid indicators are rejected before invoking the store. */
public final class IndicatorLookupCommand extends LocalQueryCommand {
    public IndicatorLookupCommand(DatasetStore store) { super(store); }
    @Override
    protected ToolResult select(ToolCall call) {
        IndicatorLookupQuery.parse(call);
        return store.lookup(call);
    }
}
