package sentinel.domain;

import java.util.List;

/** Immutable retrieval outcome; empty matches are distinct from failed execution. */
public record ToolResult(String status, List<Evidence> evidence, String sourceVersion,
                         long durationMillis, boolean truncated, ActivitySummary activitySummary, IndicatorMatch indicatorMatch) {
    public ToolResult(String status, List<Evidence> evidence, String sourceVersion,
                      long durationMillis, boolean truncated) {
        this(status, evidence, sourceVersion, durationMillis, truncated, null);
    }

    public ToolResult(String status, List<Evidence> evidence, String sourceVersion,
                      long durationMillis, boolean truncated, ActivitySummary activitySummary) {
        this(status, evidence, sourceVersion, durationMillis, truncated, activitySummary, null);
    }

    public ToolResult {
        DomainValidation.requiredText(status, "status");
        evidence = List.copyOf(DomainValidation.required(evidence, "evidence"));
        DomainValidation.requiredText(sourceVersion, "sourceVersion");
        if (durationMillis < 0) throw new IllegalArgumentException("durationMillis must not be negative.");
    }
}
