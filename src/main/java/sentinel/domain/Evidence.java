package sentinel.domain;

import java.util.UUID;

/** Immutable source-linked evidence; payload interpretation belongs to the tools. */
public record Evidence(UUID id, String sourceEventId, String datasetVersion, String provenance, String payload) {
    public Evidence {
        DomainValidation.required(id, "id");
        DomainValidation.requiredText(sourceEventId, "sourceEventId");
        DomainValidation.requiredText(datasetVersion, "datasetVersion");
        DomainValidation.requiredText(provenance, "provenance");
        DomainValidation.requiredText(payload, "payload");
    }
}
