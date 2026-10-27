package sentinel.domain;

import java.time.Instant;
import java.util.Set;

/** Validated, normalized source-dated local indicator context. */
public record IndicatorRecord(String indicator, String verdict, String source, Instant observedAt, String feedVersion) {
    public IndicatorRecord {
        indicator = Indicator.normalize(indicator);
        DomainValidation.requiredText(verdict, "verdict");
        if (!Set.of("MALICIOUS", "SUSPICIOUS", "BENIGN", "UNKNOWN").contains(verdict)) {
            throw new IllegalArgumentException("Unsupported indicator verdict.");
        }
        DomainValidation.requiredText(source, "source");
        DomainValidation.required(observedAt, "observedAt");
        DomainValidation.requiredText(feedVersion, "feedVersion");
    }
}
