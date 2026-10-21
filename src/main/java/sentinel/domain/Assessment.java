package sentinel.domain;

import java.util.List;
import java.util.UUID;

/** Immutable snapshot value. Feature-specific semantic validation belongs to analysis. */
public record Assessment(List<Event> timeline, List<Hypothesis> hypotheses, Severity severity, List<String> recommendations, Completeness completeness) {
    public Assessment {
        timeline = List.copyOf(DomainValidation.required(timeline, "timeline"));
        hypotheses = List.copyOf(DomainValidation.required(hypotheses, "hypotheses"));
        DomainValidation.required(severity, "severity");
        recommendations = List.copyOf(DomainValidation.required(recommendations, "recommendations"));
        DomainValidation.required(completeness, "completeness");
    }
}
