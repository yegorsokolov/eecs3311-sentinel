package sentinel.domain;

import java.nio.file.Path;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import sentinel.domain.*;

/** Stage 1 contract scaffold; business behavior is pending. */
public record Plan(UUID id, int revision, List<ToolCall> steps) {
    public Optional<ToolCall> next(Set<UUID> done) {
        throw new UnsupportedOperationException("Skeleton only: implementation pending");
    }
}
