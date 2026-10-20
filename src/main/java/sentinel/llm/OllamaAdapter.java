package sentinel.llm;

import java.nio.file.Path;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import sentinel.domain.*;

/** Stage 1 contract scaffold; business behavior is pending. */
public class OllamaAdapter implements LLMGateway {
    private final int timeoutSeconds = 60;
    private final URI endpoint;
    private final String model;
    public OllamaAdapter(URI endpoint, String model) {
        this.endpoint = Objects.requireNonNull(endpoint);
        this.model = Objects.requireNonNull(model);
    }
    public String complete(Prompt prompt) {
        throw new UnsupportedOperationException("Skeleton only: implementation pending");
    }
}
