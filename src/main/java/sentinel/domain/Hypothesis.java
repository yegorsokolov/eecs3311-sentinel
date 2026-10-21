package sentinel.domain;

import java.util.List;
import java.util.UUID;

/** Immutable snapshot value. Feature-specific semantic validation belongs to analysis. */
public record Hypothesis(String text, List<UUID> supportingEvidenceIds, List<UUID> contradictingEvidenceIds, String confidence, List<String> limitations) {
    public Hypothesis {
        DomainValidation.requiredText(text, "text");
        DomainValidation.requiredText(confidence, "confidence");
        supportingEvidenceIds = List.copyOf(DomainValidation.required(supportingEvidenceIds, "supportingEvidenceIds"));
        contradictingEvidenceIds = List.copyOf(DomainValidation.required(contradictingEvidenceIds, "contradictingEvidenceIds"));
        limitations = List.copyOf(DomainValidation.required(limitations, "limitations"));
    }
}
