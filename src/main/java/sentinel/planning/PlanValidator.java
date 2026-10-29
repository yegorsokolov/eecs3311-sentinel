package sentinel.planning;

import java.util.*;
import sentinel.data.CaseEvidence;
import sentinel.data.DatasetStore;
import sentinel.domain.*;
import sentinel.tools.*;
import static sentinel.planning.PlanningException.Code.*;

/** Strategy-independent validation uses the same Command registry/scope checks as execution. */
public final class PlanValidator {
    private final ToolExecutor tools;
    private final DatasetStore store;

    public PlanValidator(ToolExecutor tools, DatasetStore store) {
        this.tools = Objects.requireNonNull(tools);
        this.store = Objects.requireNonNull(store);
    }

    public Plan validate(Plan plan, CaseContext context) {
        if (context == null || context.snapshot().status() == CaseStatus.FAILED) {
            throw failure(INVALID_CONTEXT, "Planning requires an active case.");
        }
        CaseSnapshot snapshot = context.snapshot();
        if (snapshot.scope() == null) throw failure(CLARIFICATION_REQUIRED, "Provide dataset, target and UTC interval.");
        if (plan == null) throw failure(INVALID_PLAN, "Planning produced no plan.");
        if (plan.origin() == Plan.Origin.RULE && !context.ruleFallbackAccepted()) {
            throw failure(FALLBACK_APPROVAL_REQUIRED, "A basic rule plan requires explicit analyst approval.");
        }
        if (plan.revision() > 2) throw failure(REVISION_LIMIT, "Plan revision ceiling reached.");
        int expected = snapshot.plan() == null ? 0 : snapshot.plan().equals(plan)
                ? snapshot.plan().revision() : snapshot.plan().revision() + 1;
        if (plan.revision() != expected) throw failure(INVALID_PLAN, "Plan revision does not match case context.");
        if (!store.datasetId().equals(snapshot.scope().datasetId())) {
            throw failure(INVALID_CONTEXT, "Selected dataset is unavailable.");
        }
        try { store.events(snapshot.scope().datasetVersion()); }
        catch (IllegalArgumentException ex) { throw failure(INVALID_CONTEXT, "Selected dataset version is unavailable."); }
        var session = tools.openSession(CaseEvidence.scope(store, snapshot));
        if (plan.steps().size() > session.attemptsRemaining()) {
            throw failure(INVALID_PLAN, "Plan exceeds the configured tool-attempt budget.");
        }
        var keys = new HashSet<String>();
        var preceding = new HashSet<UUID>();
        Set<UUID> ids = plan.steps().stream().map(ToolCall::id).collect(java.util.stream.Collectors.toSet());
        if (!ids.containsAll(plan.dependencies().keySet())) {
            throw failure(DEPENDENCY_ERROR, "Dependencies name an unknown step.");
        }
        for (ToolCall step : plan.steps()) {
            List<UUID> dependencies = plan.dependencies().getOrDefault(step.id(), List.of());
            if (new HashSet<>(dependencies).size() != dependencies.size() || !preceding.containsAll(dependencies)) {
                throw failure(DEPENDENCY_ERROR, "Prerequisites must be unique earlier steps, without cycles or forward references.");
            }
            try { session.validate(step); }
            catch (ToolException ex) { throw failure(INVALID_PLAN, "Plan contains an unregistered, invalid or out-of-scope call."); }
            if (!keys.add(step.key())) throw failure(INVALID_PLAN, "Plan repeats an equivalent call.");
            preceding.add(step.id());
        }
        return plan;
    }

    private static PlanningException failure(PlanningException.Code code, String message) {
        return new PlanningException(code, message);
    }
}
