package sentinel.tools;

import java.nio.file.Path;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import sentinel.domain.*;
import sentinel.data.DatasetStore;

/** Stage 1 contract scaffold; business behavior is pending. */
public abstract class LocalQueryCommand implements ToolCommand {
    protected final DatasetStore store;
    protected LocalQueryCommand(DatasetStore store) { this.store = Objects.requireNonNull(store); }
    public ToolResult execute(ToolCall call) {
        throw new UnsupportedOperationException("Skeleton only: implementation pending");
    }
    protected abstract ToolResult select(ToolCall call);
}
