package sentinel.llm;

import java.nio.file.Path;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import sentinel.domain.*;

/** Stage 1 contract scaffold; business behavior is pending. */
public interface LLMGateway {
    String complete(Prompt prompt);
}
