package sentinel.planning;

/** Safe planning outcomes; clarification/fallback approval preserve the same draft case. */
public final class PlanningException extends RuntimeException {
    public enum Code { CLARIFICATION_REQUIRED, FALLBACK_APPROVAL_REQUIRED, INVALID_PLAN,
        INVALID_CONTEXT, DEPENDENCY_ERROR, REVISION_LIMIT, MODEL_UNAVAILABLE }
    private final Code code;
    public PlanningException(Code code, String message) {
        super(message);
        this.code = java.util.Objects.requireNonNull(code);
    }
    public Code code() { return code; }
}
