package sentinel.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Durable case action; STATE_CHANGED outcomes use CaseStatus names. */
public record AuditEntry(Instant timestamp, String action, String outcome, List<UUID> evidenceIds) {
    public AuditEntry {
        DomainValidation.required(timestamp, "timestamp");
        DomainValidation.requiredText(action, "action");
        DomainValidation.requiredText(outcome, "outcome");
        evidenceIds = List.copyOf(DomainValidation.required(evidenceIds, "evidenceIds"));
    }

    public static AuditEntry transition(Instant time, CaseStatus target) {
        return new AuditEntry(time, "STATE_CHANGED",
                DomainValidation.required(target, "target").name(), List.of());
    }
}
