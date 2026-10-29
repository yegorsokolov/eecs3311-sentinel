package sentinel.planning;

import sentinel.domain.*;

/** Replaceable plan creation; semantic validation belongs to PlanValidator. */
@FunctionalInterface
public interface PlanningStrategy {
    Plan createPlan(CaseContext context);
}
