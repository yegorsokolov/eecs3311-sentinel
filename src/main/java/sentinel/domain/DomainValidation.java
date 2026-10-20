package sentinel.domain;

/** Shared boundary checks; never echo user data in error messages. */
final class DomainValidation {
    private DomainValidation() { }

    static String requiredText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank.");
        }
        return value;
    }

    static <T> T required(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " must not be null.");
        }
        return value;
    }
}
