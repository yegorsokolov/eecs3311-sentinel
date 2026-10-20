package sentinel.planning;

import java.nio.file.Path;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import sentinel.domain.*;

/** Stage 1 contract scaffold; business behavior is pending. */
public class RulePlanningStrategy implements PlanningStrategy {
    private final PlanValidator validator;
    public RulePlanningStrategy(PlanValidator validator) {
        this.validator = Objects.requireNonNull(validator);
    }
    public Plan createPlan(CaseContext context) {
        throw new UnsupportedOperationException("Skeleton only: implementation pending");
    }
}
