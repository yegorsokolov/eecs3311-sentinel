package sentinel.tools;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import sentinel.domain.CaseScope;
import sentinel.domain.Indicator;

/**
 * Trusted application input for one execution session, never model-supplied authorization.
 * Evidence indicators must come from already accepted in-scope case evidence.
 * Explicit target kind avoids guessing whether an account name is a domain.
 */
public record ToolScope(CaseScope caseScope, TargetKind targetKind, String feedVersion,
                        Set<String> evidenceIndicators) {
    public enum TargetKind { ACCOUNT, INDICATOR }

    public ToolScope {
        Objects.requireNonNull(caseScope, "caseScope");
        Objects.requireNonNull(targetKind, "targetKind");
        if (targetKind == TargetKind.INDICATOR) Indicator.normalize(caseScope.target());
        if (feedVersion != null && feedVersion.isBlank()) {
            throw new IllegalArgumentException("Selected feedVersion must not be blank.");
        }
        evidenceIndicators = Objects.requireNonNull(evidenceIndicators, "evidenceIndicators").stream()
                .map(Indicator::normalize).collect(Collectors.toUnmodifiableSet());
    }

    public static ToolScope account(CaseScope scope, String feedVersion, Set<String> evidenceIndicators) {
        return new ToolScope(scope, TargetKind.ACCOUNT, feedVersion, evidenceIndicators);
    }

    public static ToolScope indicator(CaseScope scope, String feedVersion) {
        return new ToolScope(scope, TargetKind.INDICATOR, feedVersion, Set.of());
    }

    String normalizedTarget() {
        return targetKind == TargetKind.INDICATOR ? Indicator.normalize(caseScope.target()) : caseScope.target();
    }
}
