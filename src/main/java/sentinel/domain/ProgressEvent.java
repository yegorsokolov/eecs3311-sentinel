package sentinel.domain;

import java.util.List;
import java.util.UUID;

/** Ephemeral observer notification; durable actions belong in Investigation history. */
public record ProgressEvent(String stage, String outcome, List<UUID> evidenceIds) {
    public ProgressEvent {
        DomainValidation.requiredText(stage, "stage");
        DomainValidation.requiredText(outcome, "outcome");
        evidenceIds = List.copyOf(DomainValidation.required(evidenceIds, "evidenceIds"));
    }
}
