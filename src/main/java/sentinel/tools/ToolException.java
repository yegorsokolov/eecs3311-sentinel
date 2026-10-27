package sentinel.tools;

/** Typed retrieval failure; empty matches are returned normally, not thrown. */
public final class ToolException extends RuntimeException {
    public enum Code { INVALID_ARGUMENT, UNKNOWN_DATASET, UNKNOWN_VERSION, FEED_UNAVAILABLE, INVALID_FEED, UNKNOWN_FEED_VERSION }
    private final Code code;

    public ToolException(Code code, String message) {
        super(message);
        this.code = java.util.Objects.requireNonNull(code);
    }

    public Code code() { return code; }
}
