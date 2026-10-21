package sentinel.domain;

import java.util.List;
import java.util.UUID;

/** Immutable snapshot value. Feature-specific semantic validation belongs to analysis. */
public record Answer(String text, List<UUID> evidenceIds, List<String> limitations) {
    public Answer {
        DomainValidation.requiredText(text, "text");
        evidenceIds = List.copyOf(DomainValidation.required(evidenceIds, "evidenceIds"));
        limitations = List.copyOf(DomainValidation.required(limitations, "limitations"));
    }
}
