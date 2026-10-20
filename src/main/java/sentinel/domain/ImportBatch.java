package sentinel.domain;

import java.util.List;

/**
 * Immutable candidate events for later atomic ingestion.
 * Duplicate detection and dataset-version assignment belong to the importer/store.
 */
public record ImportBatch(List<Event> events) {
    public ImportBatch {
        DomainValidation.required(events, "events");
        if (events.stream().anyMatch(event -> event == null)) {
            throw new IllegalArgumentException("events must not contain null entries.");
        }
        events = List.copyOf(events);
    }
}
