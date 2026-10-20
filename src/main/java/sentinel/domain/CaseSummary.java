package sentinel.domain;

import java.nio.file.Path;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import sentinel.domain.*;

/** Stage 1 contract scaffold; business behavior is pending. */
public record CaseSummary(UUID id, int version, CaseStatus status, String goal) {
    // TODO: Validate invariants and defensively copy collection values.
}
