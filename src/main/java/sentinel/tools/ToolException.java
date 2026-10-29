package sentinel.tools;

/** Typed retrieval failure; empty matches are returned normally, not thrown. */
public final class ToolException extends RuntimeException {
    public enum Code { INVALID_ARGUMENT, UNKNOWN_DATASET, UNKNOWN_VERSION, FEED_UNAVAILABLE, INVALID_FEED, UNKNOWN_FEED_VERSION,
        INVALID_RESULT, UNKNOWN_TOOL, SCOPE_REQUIRED, OUT_OF_SCOPE, DUPLICATE_CALL, BUDGET_EXHAUSTED,
        TIMEOUT, INTERRUPTED, TRANSIENT_FAILURE, EXECUTION_FAILED, EXECUTOR_BUSY, EXECUTOR_CLOSED }
    private final Code code;

    public ToolException(Code code, String message) {
        super(message);
        this.code = java.util.Objects.requireNonNull(code);
    }

    public Code code() { return code; }
}
