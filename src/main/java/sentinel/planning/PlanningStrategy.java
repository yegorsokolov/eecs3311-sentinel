package sentinel.planning;

import sentinel.domain.*;

/** Planning returns a semantically validated plan or a typed failure, without running tools. */
@FunctionalInterface
public interface PlanningStrategy {
    Plan createPlan(CaseContext context);
    default Plan createPlan(CaseContext context, sentinel.llm.ModelBudget budget) { return createPlan(context); }
    default String modelVersion() { return null; }
    default String promptVersion() { return null; }
}
