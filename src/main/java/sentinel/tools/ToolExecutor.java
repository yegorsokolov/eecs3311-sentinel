package sentinel.tools;

import java.nio.file.Path;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import sentinel.domain.*;

/** Stage 1 contract scaffold; business behavior is pending. */
public class ToolExecutor {
    private final Map<String, ToolCommand> commands;
    public ToolExecutor(Map<String, ToolCommand> commands) {
        this.commands = Objects.requireNonNull(commands);
    }
    public ToolResult execute(ToolCall call) {
        throw new UnsupportedOperationException("Skeleton only: implementation pending");
    }
    public void validate(ToolCall call) {
        throw new UnsupportedOperationException("Skeleton only: implementation pending");
    }
}
