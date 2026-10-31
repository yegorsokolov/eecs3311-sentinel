package sentinel.llm;

import sentinel.domain.Prompt;

/** Adapter port: one completion attempt, with retry/repair accounting owned by the application. */
@FunctionalInterface
public interface LLMGateway {
    String complete(Prompt prompt);
    /** Known provider/model identifier; null means unknown (for example, an injected test gateway). */
    default String modelVersion() { return null; }
}
