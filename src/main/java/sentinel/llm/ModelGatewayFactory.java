package sentinel.llm;

import sentinel.config.AppConfig;
import java.time.Duration;

/** Composition boundary: application/planning code depends only on LLMGateway. */
public final class ModelGatewayFactory {
    private ModelGatewayFactory() { }
    public static LLMGateway create(AppConfig config) {
        var model = config.modelSettings();
        var timeout = Duration.ofSeconds(config.limits().modelTimeoutSeconds());
        return switch (model.provider()) {
            case "ollama" -> new OllamaAdapter(model.baseUrl(), model.model(), timeout,
                    OllamaAdapter.MAX_REQUEST_BYTES, OllamaAdapter.MAX_RESPONSE_BYTES);
            case "openai-compatible" -> new OpenAICompatibleAdapter(model, timeout);
            default -> throw new IllegalArgumentException("Unsupported model protocol.");
        };
    }
}
