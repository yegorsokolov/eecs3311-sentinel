package sentinel.domain;

import java.nio.file.Path;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import sentinel.domain.*;

/** Stage 1 contract scaffold; business behavior is pending. */
public record Event(String eventId, Instant timestamp, String eventType, String accountId, String sourceIp, String hostId, String outcome, String sourcePosition, String datasetVersion) {
    // TODO: Validate invariants and defensively copy collection values.
}
