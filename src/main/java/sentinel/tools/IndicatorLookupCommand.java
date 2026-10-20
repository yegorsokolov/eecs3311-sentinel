package sentinel.tools;

import java.nio.file.Path;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import sentinel.domain.*;
import sentinel.data.DatasetStore;

/** Stage 1 contract scaffold; business behavior is pending. */
public class IndicatorLookupCommand extends LocalQueryCommand {
    public IndicatorLookupCommand(DatasetStore store) { super(store); }
    @Override
    protected ToolResult select(ToolCall call) {
        throw new UnsupportedOperationException("Skeleton only: implementation pending");
    }
}
