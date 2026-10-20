package sentinel.domain;

import java.time.Instant;

/**
 * A normalized event with recoverable source provenance.
 * Optional account/network/host/outcome details remain null when unavailable.
 * Identifier and source text are preserved; importer-specific normalization is separate.
 */
public record Event(String eventId, Instant timestamp, String eventType, String accountId,
                    String sourceIp, String hostId, String outcome, String sourcePosition,
                    String datasetVersion) {
    public Event {
        DomainValidation.requiredText(eventId, "eventId");
        DomainValidation.required(timestamp, "timestamp");
        DomainValidation.requiredText(eventType, "eventType");
        DomainValidation.requiredText(sourcePosition, "sourcePosition");
        DomainValidation.requiredText(datasetVersion, "datasetVersion");
    }
}
