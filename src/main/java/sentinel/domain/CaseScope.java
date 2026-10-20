package sentinel.domain;

import java.time.Instant;

/**
 * A complete investigation scope. Instants represent UTC timeline positions.
 * Partial user input belongs in the clarification workflow, not in this value.
 */
public record CaseScope(String datasetId, String datasetVersion, String target,
                        Instant start, Instant end) {
    public CaseScope {
        DomainValidation.requiredText(datasetId, "datasetId");
        DomainValidation.requiredText(datasetVersion, "datasetVersion");
        DomainValidation.requiredText(target, "target");
        DomainValidation.required(start, "start");
        DomainValidation.required(end, "end");
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("end must not precede start.");
        }
    }
}
