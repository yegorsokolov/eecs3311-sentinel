package sentinel.config;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/** Immutable startup settings. Loading creates no directories and makes no network calls. */
public record AppConfig(URI ollamaUrl, String ollamaModel, Path datasetDirectory,
                        Path caseDirectory, Path exportDirectory, ExecutionLimits limits) {
    private static final Map<String, String> OVERRIDES = Map.of(
            "SENTINEL_OLLAMA_URL", "ollama.url",
            "SENTINEL_OLLAMA_MODEL", "ollama.model",
            "SENTINEL_DATA_DIR", "paths.datasets",
            "SENTINEL_CASE_DIR", "paths.cases",
            "SENTINEL_EXPORT_DIR", "paths.exports");

    /** Stage 1 budgets include retries and model repair calls. */
    public record ExecutionLimits(int toolAttempts, int planRevisions, int modelCalls,
                                  int toolTimeoutSeconds, int modelTimeoutSeconds,
                                  int transientRetries) { }

    public static AppConfig load() {
        return load(Path.of(""), System.getenv());
    }

    // Explicit inputs isolate tests from machine settings.
    static AppConfig load(Path workingDirectory, Map<String, String> environment) {
        Path root = workingDirectory.toAbsolutePath().normalize();
        Properties settings = defaults();
        apply(settings, readEnv(root.resolve(".env")));
        apply(settings, environment);
        URI endpoint;
        try {
            endpoint = URI.create(settings.getProperty("ollama.url"));
        } catch (IllegalArgumentException ex) {
            throw new ConfigurationException("SENTINEL_OLLAMA_URL must be an absolute HTTP(S) URL.");
        }
        if (!("http".equalsIgnoreCase(endpoint.getScheme()) || "https".equalsIgnoreCase(endpoint.getScheme()))
                || endpoint.getHost() == null || endpoint.getUserInfo() != null
                || endpoint.getQuery() != null || endpoint.getFragment() != null
                || endpoint.getPort() > 65535 || endpoint.getPort() == 0) {
            throw new ConfigurationException("SENTINEL_OLLAMA_URL requires an HTTP(S) host, valid port and no credentials, query or fragment.");
        }
        String model = settings.getProperty("ollama.model");
        if (!model.matches("[A-Za-z0-9][A-Za-z0-9._:/-]*")) {
            throw new ConfigurationException("SENTINEL_OLLAMA_MODEL must be a model name without whitespace.");
        }
        return new AppConfig(endpoint, model,
                directory(root, settings, "paths.datasets"), directory(root, settings, "paths.cases"),
                directory(root, settings, "paths.exports"),
                new ExecutionLimits(number(settings, "limits.toolAttempts", 1),
                        number(settings, "limits.planRevisions", 0), number(settings, "limits.modelCalls", 1),
                        number(settings, "limits.toolTimeoutSeconds", 1), number(settings, "limits.modelTimeoutSeconds", 1),
                        number(settings, "limits.transientRetries", 0)));
    }

    private static Properties defaults() {
        Properties result = new Properties();
        try (var stream = AppConfig.class.getResourceAsStream("/application.properties")) {
            if (stream == null) throw new ConfigurationException("Missing application.properties in the application classpath.");
            result.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            return result;
        } catch (IOException ex) {
            throw new ConfigurationException("Cannot read bundled application.properties.");
        }
    }

    private static void apply(Properties settings, Map<String, String> values) {
        OVERRIDES.forEach((variable, property) -> {
            String value = values.get(variable);
            if (value != null && !value.isBlank()) settings.setProperty(property, value.strip());
        });
    }

    private static Map<String, String> readEnv(Path file) {
        Map<String, String> values = new HashMap<>();
        if (Files.notExists(file)) return values;
        try {
            int lineNumber = 0;
            for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                lineNumber++;
                String line = raw.strip();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int separator = line.indexOf('=');
                if (separator < 1) throw malformed(lineNumber);
                String key = line.substring(0, separator).strip();
                if (!key.matches("[A-Za-z_][A-Za-z0-9_]*")) throw malformed(lineNumber);
                String value = line.substring(separator + 1).strip();
                if (value.startsWith("\"") || value.startsWith("'")) {
                    if (value.length() < 2 || value.charAt(value.length() - 1) != value.charAt(0)) {
                        throw malformed(lineNumber);
                    }
                    value = value.substring(1, value.length() - 1);
                }
                values.put(key, value);
            }
            return values;
        } catch (IOException ex) {
            throw new ConfigurationException("Cannot read local .env; check file permissions and UTF-8 encoding.");
        }
    }

    private static ConfigurationException malformed(int line) {
        // Original lines may contain credentials; never echo their contents.
        return new ConfigurationException("Invalid .env entry at line " + line + "; expected KEY=value with optional matching quotes.");
    }

    private static Path directory(Path root, Properties settings, String key) {
        try {
            Path path = root.resolve(settings.getProperty(key)).normalize();
            if (Files.exists(path) && !Files.isDirectory(path)) {
                throw new ConfigurationException(key + " must refer to a directory, not a file.");
            }
            return path;
        } catch (InvalidPathException ex) {
            throw new ConfigurationException(key + " contains an invalid filesystem path.");
        }
    }

    private static int number(Properties settings, String key, int minimum) {
        try {
            int value = Integer.parseInt(settings.getProperty(key));
            if (value < minimum) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException ex) {
            throw new ConfigurationException("Invalid bundled setting: " + key + ".");
        }
    }
}
