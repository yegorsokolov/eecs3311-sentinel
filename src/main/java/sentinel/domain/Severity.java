package sentinel.domain;

import java.util.List;
import java.util.UUID;

/** Immutable snapshot value. Feature-specific semantic validation belongs to analysis. */
public record Severity(int score, SeverityLevel level, List<String> ruleIds, String policyVersion, List<String> limitations) {
    public Severity {
        DomainValidation.required(level, "level");
        DomainValidation.requiredText(policyVersion, "policyVersion");
        ruleIds = List.copyOf(DomainValidation.required(ruleIds, "ruleIds"));
        limitations = List.copyOf(DomainValidation.required(limitations, "limitations"));
    }
}
