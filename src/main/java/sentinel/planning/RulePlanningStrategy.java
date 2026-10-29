package sentinel.planning;

import java.util.*;
import sentinel.data.CaseEvidence;
import sentinel.data.DatasetStore;
import sentinel.domain.*;
import static sentinel.planning.PlanningException.Code.*;

/** Deterministic, explicitly approved basic collection plan; never presented as AI output. */
public final class RulePlanningStrategy implements PlanningStrategy {
    private final PlanValidator validator;
    private final DatasetStore store;

    public RulePlanningStrategy(PlanValidator validator, DatasetStore store) {
        this.validator = Objects.requireNonNull(validator);
        this.store = Objects.requireNonNull(store);
    }

    public Plan createPlan(CaseContext context) {
        Objects.requireNonNull(context, "context");
        var snapshot = context.snapshot();
        CaseScope scope = snapshot.scope();
        if (scope == null) throw new PlanningException(CLARIFICATION_REQUIRED, "Provide dataset, target and UTC interval.");
        if (!context.ruleFallbackAccepted()) {
            throw new PlanningException(FALLBACK_APPROVAL_REQUIRED, "Approve the labeled basic rule plan before use.");
        }
        var steps = new ArrayList<ToolCall>();
        var dependencies = new HashMap<UUID, List<UUID>>();
        boolean account = snapshot.targetType() == TargetType.ACCOUNT;
        boolean ip = !account && (Indicator.normalize(scope.target()).contains(":")
                || Indicator.normalize(scope.target()).matches("[0-9]+(?:\\.[0-9]+){3}"));
        if (account || ip) {
            var args = new HashMap<>(Map.of("datasetId", scope.datasetId(), "datasetVersion", scope.datasetVersion(),
                    "start", scope.start().toString(), "end", scope.end().toString(), "limit", "100"));
            args.put(account ? "accountId" : "sourceIp", account ? scope.target() : Indicator.normalize(scope.target()));
            ToolCall search = new ToolCall(UUID.randomUUID(), "logSearch", args);
            steps.add(search);
            if (account) {
                var activity = new ToolCall(UUID.randomUUID(), "userActivity", args);
                steps.add(activity);
                dependencies.put(activity.id(), List.of(search.id()));
            }
        }
        if (snapshot.feedVersion() != null) {
            Set<String> indicators = account ? CaseEvidence.indicators(store, snapshot)
                    : Set.of(Indicator.normalize(scope.target()));
            indicators.stream().sorted().limit(8 - steps.size()).forEach(indicator -> steps.add(
                    new ToolCall(UUID.randomUUID(), "indicatorLookup",
                            Map.of("indicator", indicator, "feedVersion", snapshot.feedVersion()))));
        }
        if (steps.isEmpty()) throw new PlanningException(CLARIFICATION_REQUIRED, "Domain investigation requires a selected feed.");
        int revision = snapshot.plan() == null ? 0 : snapshot.plan().revision() + 1;
        return validator.validate(new Plan(UUID.randomUUID(), revision, steps, dependencies, Plan.Origin.RULE), context);
    }
}
