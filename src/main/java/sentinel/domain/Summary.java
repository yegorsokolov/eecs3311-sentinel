package sentinel.domain;

import java.util.List;

/** Import result. Rejected batches always report zero committed records and duplicates. */
public record Summary(String datasetVersion, int accepted, int duplicates, List<String> errors) {
    public Summary {
        DomainValidation.requiredText(datasetVersion, "datasetVersion");
        if (accepted < 0 || duplicates < 0) throw new IllegalArgumentException("Counts must not be negative.");
        errors = List.copyOf(DomainValidation.required(errors, "errors"));
        if (!errors.isEmpty() && (accepted != 0 || duplicates != 0)) {
            throw new IllegalArgumentException("Rejected imports cannot report committed counts.");
        }
    }

    public boolean successful() {
        return errors.isEmpty();
    }
}
