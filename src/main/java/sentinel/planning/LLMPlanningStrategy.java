package sentinel.planning;

import java.util.Objects;
import sentinel.domain.*;
import sentinel.llm.*;

/** Validated model Strategy with one bounded repair and explicit, separately approved rule fallback. */
public final class LLMPlanningStrategy implements PlanningStrategy {
    private final LLMGateway llm;
    private final PlanValidator validator;

    public LLMPlanningStrategy(LLMGateway llm, PlanValidator validator) {
        this.llm = Objects.requireNonNull(llm);
        this.validator = Objects.requireNonNull(validator);
    }

    @Override public String modelVersion() { return llm.modelVersion(); }
    @Override public String promptVersion() { return PlanningPrompt.VERSION; }

    @Override public Plan createPlan(CaseContext context) { return createPlan(context, new ModelBudget()); }

    /** Supply the same budget to subsequent model work in an autonomous run; never reset it on repair. */
    public Plan createPlan(CaseContext context, ModelBudget budget) {
        Objects.requireNonNull(budget, "budget");
        validator.validateContext(context);
        if (context.snapshot().plan() != null && context.snapshot().plan().revision() >= 2) {
            throw new PlanningException(PlanningException.Code.REVISION_LIMIT, "Plan revision ceiling reached.");
        }
        String repairCode = null;
        boolean[] transientRetryUsed = {false};
        for (int draft = 0; draft < 2; draft++) {
            Prompt prompt = PlanningPrompt.build(context, validator, repairCode);
            String reply;
            try { reply = complete(prompt, budget, transientRetryUsed); }
            catch (ModelException ex) {
                throw new PlanningException(ex.code() == ModelException.Code.BUDGET_EXHAUSTED
                        ? PlanningException.Code.MODEL_BUDGET_EXHAUSTED : PlanningException.Code.MODEL_UNAVAILABLE,
                        "Model planning is unavailable (" + ex.code().name() + "); a labeled rule plan requires analyst approval.");
            }
            try { return validator.validate(ModelPlanParser.parse(reply, context), context); }
            catch (PlanningException ex) {
                if (ex.code() != PlanningException.Code.INVALID_PLAN && ex.code() != PlanningException.Code.DEPENDENCY_ERROR) throw ex;
                repairCode = ex.code().name();
            }
        }
        throw new PlanningException(PlanningException.Code.INVALID_PLAN,
                "Model plan remained invalid after one repair; a labeled rule plan requires analyst approval.");
    }

    private String complete(Prompt prompt, ModelBudget budget, boolean[] transientRetryUsed) {
        while (true) {
            budget.acquire();
            try { return llm.complete(prompt); }
            catch (ModelException ex) {
                if (ex.transientFailure() && !transientRetryUsed[0]) {
                    transientRetryUsed[0] = true;
                    continue;
                }
                throw ex;
            }
        }
    }
}
