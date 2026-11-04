package sentinel.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.net.URI;
import java.util.Map;
import sentinel.llm.*;
import static org.junit.jupiter.api.Assertions.*;

class ModelSettingsTest {
    @TempDir Path root;
    @Test void defaultFactoryUsesOllama() {
        var config = AppConfig.load(root, Map.of());
        assertInstanceOf(OllamaAdapter.class, ModelGatewayFactory.create(config));
        assertEquals("qwen2.5:7b", config.modelSettings().model());
    }
    @Test void canonicalAndLegacyOverridesRespectLayerPrecedence() throws Exception {
        Files.writeString(root.resolve(".env"), "SENTINEL_LLM_MODEL=file\nSENTINEL_LLM_BASE_URL=http://localhost:1234\n");
        var config = AppConfig.load(root, Map.of("SENTINEL_OLLAMA_MODEL", "process",
                "SENTINEL_OLLAMA_URL", "http://localhost:4321"));
        assertEquals("process", config.modelSettings().model());
        assertEquals(4321, config.modelSettings().baseUrl().getPort());
        assertEquals("canonical", AppConfig.load(root, Map.of("SENTINEL_OLLAMA_MODEL", "legacy",
                "SENTINEL_LLM_MODEL", "canonical")).modelSettings().model());
    }
    @Test void hostedFactoryAndSecretRedaction() {
        var config = AppConfig.load(root, Map.of("SENTINEL_LLM_PROVIDER", "openai-compatible",
                "SENTINEL_LLM_BASE_URL", "https://example.com/v1", "SENTINEL_LLM_API_KEY", "private-key",
                "SENTINEL_LLM_MODEL", "provider/model", "SENTINEL_LLM_JSON_MODE", "false",
                "SENTINEL_LLM_TOKEN_FIELD", "max_completion_tokens"));
        assertInstanceOf(OpenAICompatibleAdapter.class, ModelGatewayFactory.create(config));
        assertFalse(config.toString().contains("private-key"));
        assertFalse(config.modelSettings().jsonMode());
    }
    @Test void rejectsInvalidProviderAndOptions() {
        for (var settings : java.util.List.of(Map.of("SENTINEL_LLM_PROVIDER", "unknown"),
                Map.of("SENTINEL_LLM_PROVIDER", "openai-compatible"),
                Map.of("SENTINEL_LLM_JSON_MODE", "yes"), Map.of("SENTINEL_LLM_TOKEN_FIELD", "arbitrary")))
            assertThrows(ConfigurationException.class, () -> AppConfig.load(root, settings));
    }
    @Test void refusesCredentialLeakageAndHeaderInjection() {
        for (String url : java.util.List.of("http://example.com/v1", "https://user:secret@example.com/v1", "https://example.com?key=secret")) {
            var ex = assertThrows(ConfigurationException.class, () -> new ModelSettings("openai-compatible",
                    URI.create(url), "qwen", "secret", true, "max_tokens"));
            assertFalse(ex.getMessage().contains("secret"));
        }
        assertThrows(ConfigurationException.class, () -> new ModelSettings("openai-compatible",
                URI.create("https://example.com"), "qwen", "secret\r\nInjected: value", true, "max_tokens"));
    }
    @Test void blankEnvironmentRetainsLocalKeyWithoutPrintingIt() throws Exception {
        Files.writeString(root.resolve(".env"), "SENTINEL_LLM_PROVIDER=openai-compatible\nSENTINEL_LLM_BASE_URL=https://example.com/v1\nSENTINEL_LLM_API_KEY=private-key\n");
        var config = AppConfig.load(root, Map.of("SENTINEL_LLM_API_KEY", ""));
        assertEquals("private-key", config.modelSettings().apiKey());
        assertFalse(config.toString().contains("private-key"));
    }
}
