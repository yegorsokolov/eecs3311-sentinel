package sentinel.domain;

/** Immutable planning input; rule fallback approval is supplied explicitly by the application. */
public record CaseContext(CaseSnapshot snapshot, boolean ruleFallbackAccepted) {
    public CaseContext(CaseSnapshot snapshot) { this(snapshot, false); }
    public CaseContext { DomainValidation.required(snapshot, "snapshot"); }
}
