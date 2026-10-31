package sentinel.llm;

/** Safe provider/budget outcomes. Messages never contain prompts, raw responses or error bodies. */
public final class ModelException extends RuntimeException {
    public enum Code { INVALID_REQUEST, UNAVAILABLE, TIMEOUT, INTERRUPTED, HTTP_ERROR, AUTHENTICATION, MODEL_NOT_FOUND,
        TRANSIENT_FAILURE, INVALID_RESPONSE, RESPONSE_TOO_LARGE, BUDGET_EXHAUSTED }
    private final Code code;

    public ModelException(Code code, String message) {
        super(message);
        this.code = java.util.Objects.requireNonNull(code);
    }
    public Code code() { return code; }
    public boolean transientFailure() { return code == Code.TRANSIENT_FAILURE; }
}
