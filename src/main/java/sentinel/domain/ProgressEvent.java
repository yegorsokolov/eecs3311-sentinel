package sentinel.domain;

import java.nio.file.Path;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import sentinel.domain.*;

/** Stage 1 contract scaffold; business behavior is pending. */
public record ProgressEvent(String stage, String outcome, List<UUID> evidenceIds) {
    // TODO: Validate invariants and defensively copy collection values.
}
