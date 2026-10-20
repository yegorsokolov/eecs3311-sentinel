package sentinel.domain;

import java.nio.file.Path;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import sentinel.domain.*;

/** Stage 1 contract scaffold; business behavior is pending. */
public record ToolCall(UUID id, String toolName, Map<String, String> args) {
    public String key() {
        throw new UnsupportedOperationException("Skeleton only: implementation pending");
    }
}
