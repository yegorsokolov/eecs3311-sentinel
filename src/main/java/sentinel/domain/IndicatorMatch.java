package sentinel.domain;

import java.time.Instant;

/** Unmatched indicators carry UNKNOWN and no invented source/date. */
public record IndicatorMatch(String indicator, String feedVersion, boolean matched, String verdict,
                             String source, Instant observedAt, boolean stale) {
    public IndicatorMatch {
        indicator = Indicator.normalize(indicator);
        DomainValidation.requiredText(feedVersion, "feedVersion");
        if (matched) {
            new IndicatorRecord(indicator, verdict, source, observedAt, feedVersion);
        } else if (!"UNKNOWN".equals(verdict) || source != null || observedAt != null || stale) {
            throw new IllegalArgumentException("Unmatched indicator context must be unknown.");
        }
    }
}
