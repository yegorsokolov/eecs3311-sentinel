package sentinel.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class AppConfigTest {
    @TempDir Path root;

    @Test void missingEnvUsesStageOneDefaultsWithoutCreatingStorage() {
        AppConfig config = AppConfig.load(root, Map.of());
        assertEquals("http://localhost:11434", config.ollamaUrl().toString());
        assertEquals("qwen2.5:7b", config.ollamaModel());
        assertEquals(new AppConfig.ExecutionLimits(8, 2, 6, 10, 60, 1), config.limits());
        assertEquals(root.resolve("runtime/datasets"), config.datasetDirectory());
        assertFalse(Files.exists(root.resolve("runtime")));
    }

    @Test void emptyEnvIsValid() throws Exception {
        Files.writeString(root.resolve(".env"), "");
        assertEquals("qwen2.5:7b", AppConfig.load(root, Map.of()).ollamaModel());
    }

    @Test void fileOverridesSupportCommentsQuotesAndSpaces() throws Exception {
        Files.writeString(root.resolve(".env"),
                "# Local overrides\nSENTINEL_OLLAMA_MODEL='qwen2.5:3b'\nSENTINEL_CASE_DIR=\"demo cases\"\n");
        AppConfig config = AppConfig.load(root, Map.of());
        assertEquals("qwen2.5:3b", config.ollamaModel());
        assertEquals(root.resolve("demo cases"), config.caseDirectory());
    }

    @Test void environmentWinsAndBlankValuesRetainLowerPrioritySettings() throws Exception {
        Files.writeString(root.resolve(".env"), "SENTINEL_OLLAMA_MODEL=file-model\nSENTINEL_CASE_DIR=\n");
        assertEquals("process-model", AppConfig.load(root,
                Map.of("SENTINEL_OLLAMA_MODEL", "process-model")).ollamaModel());
        AppConfig config = AppConfig.load(root, Map.of("SENTINEL_OLLAMA_MODEL", " "));
        assertEquals("file-model", config.ollamaModel());
        assertEquals(root.resolve("runtime/cases"), config.caseDirectory());
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-url", "ftp://localhost", "http://user:secret@localhost",
            "http://localhost?key=secret", "http://localhost#fragment", "http://localhost:70000"})
    void invalidEndpointIsRejectedWithoutEchoingValue(String value) {
        ConfigurationException ex = assertThrows(ConfigurationException.class,
                () -> AppConfig.load(root, Map.of("SENTINEL_OLLAMA_URL", value)));
        assertFalse(ex.getMessage().contains(value));
        assertFalse(ex.getMessage().contains("secret"));
    }

    @Test void malformedFileReportsLineWithoutContents() throws Exception {
        Files.writeString(root.resolve(".env"), "# comment\nSECRET_VALUE_WITHOUT_EQUALS\n");
        ConfigurationException ex = assertThrows(ConfigurationException.class,
                () -> AppConfig.load(root, Map.of()));
        assertTrue(ex.getMessage().contains("line 2"));
        assertFalse(ex.getMessage().contains("SECRET"));
    }

    @Test void unmatchedQuoteIsRejected() throws Exception {
        Files.writeString(root.resolve(".env"), "SENTINEL_OLLAMA_MODEL='unfinished\n");
        assertThrows(ConfigurationException.class, () -> AppConfig.load(root, Map.of()));
    }

    @Test void invalidModelIsRejected() {
        assertThrows(ConfigurationException.class,
                () -> AppConfig.load(root, Map.of("SENTINEL_OLLAMA_MODEL", "invalid model")));
    }

    @Test void pathCannotPointToExistingFile() throws Exception {
        Files.writeString(root.resolve("file"), "data");
        assertThrows(ConfigurationException.class,
                () -> AppConfig.load(root, Map.of("SENTINEL_CASE_DIR", "file")));
    }

    @Test void absoluteAndRelativeStoragePathsAreResolved() {
        AppConfig config = AppConfig.load(root, Map.of("SENTINEL_CASE_DIR", root.resolve("cases").toString(),
                "SENTINEL_DATA_DIR", "data/../datasets", "SENTINEL_EXPORT_DIR", "reports"));
        assertEquals(root.resolve("cases"), config.caseDirectory());
        assertEquals(root.resolve("datasets"), config.datasetDirectory());
        assertEquals(root.resolve("reports"), config.exportDirectory());
    }
}
