package sentinel.config;

/** Configuration error safe to display without echoing setting values. */
public final class ConfigurationException extends RuntimeException {
    public ConfigurationException(String message) { super(message); }
}
