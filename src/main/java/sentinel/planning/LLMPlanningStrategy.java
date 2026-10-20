package sentinel.planning;

import java.nio.file.Path;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import sentinel.domain.*;
import sentinel.llm.LLMGateway;

/** Stage 1 contract scaffold; business behavior is pending. */
public class LLMPlanningStrategy implements PlanningStrategy {
    private final LLMGateway llm;
    private final PlanValidator validator;
    public LLMPlanningStrategy(LLMGateway llm, PlanValidator validator) {
        this.llm = Objects.requireNonNull(llm);
        this.validator = Objects.requireNonNull(validator);
    }
    public Plan createPlan(CaseContext context) {
        throw new UnsupportedOperationException("Skeleton only: implementation pending");
    }
}
