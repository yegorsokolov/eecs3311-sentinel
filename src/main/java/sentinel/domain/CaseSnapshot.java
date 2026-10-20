package sentinel.domain;

import java.nio.file.Path;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import sentinel.domain.*;

/** Stage 1 contract scaffold; business behavior is pending. */
public record CaseSnapshot(int schemaVersion, int caseVersion, UUID id, CaseStatus status, CaseScope scope, Plan plan, List<Evidence> evidence, Assessment assessment, List<ConversationEntry> conversation, List<AuditEntry> history, String modelVersion, String promptVersion, String policyVersion) {
    // TODO: Validate invariants and defensively copy collection values.
}
