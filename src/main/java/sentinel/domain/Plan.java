package sentinel.domain;

import java.util.*;

/** Immutable plan; semantic scope, dependency and revision checks belong to PlanValidator. */
public record Plan(UUID id, int revision, List<ToolCall> steps,
                   Map<UUID, List<UUID>> dependencies, Origin origin) {
    public enum Origin { UNSPECIFIED, RULE, MODEL }

    public Plan(UUID id, int revision, List<ToolCall> steps) {
        this(id, revision, steps, Map.of(), Origin.UNSPECIFIED);
    }

    public Plan {
        DomainValidation.required(id, "id");
        if (revision < 0) throw new IllegalArgumentException("revision must not be negative.");
        steps = List.copyOf(DomainValidation.required(steps, "steps"));
        if (steps.isEmpty() || steps.size() > 8) throw new IllegalArgumentException("A plan must have 1 to 8 steps.");
        if (steps.stream().map(ToolCall::id).distinct().count() != steps.size()) {
            throw new IllegalArgumentException("Plan step IDs must be unique.");
        }
        var copy = new HashMap<UUID, List<UUID>>();
        DomainValidation.required(dependencies, "dependencies").forEach((key, value) -> copy.put(
                DomainValidation.required(key, "dependency step"), List.copyOf(value)));
        dependencies = Map.copyOf(copy);
        DomainValidation.required(origin, "origin");
    }

    /** Return the next eligible step; completion IDs must belong to this plan and include prerequisites. */
    public Optional<ToolCall> next(Set<UUID> done) {
        Set<UUID> completed = Set.copyOf(DomainValidation.required(done, "done"));
        Set<UUID> ids = steps.stream().map(ToolCall::id).collect(java.util.stream.Collectors.toSet());
        if (!ids.containsAll(completed)) throw new IllegalArgumentException("Unknown completed step.");
        for (UUID id : completed) {
            if (!completed.containsAll(dependencies.getOrDefault(id, List.of()))) {
                throw new IllegalArgumentException("Completed step has unfinished prerequisites.");
            }
        }
        return steps.stream().filter(step -> !completed.contains(step.id()))
                .filter(step -> completed.containsAll(dependencies.getOrDefault(step.id(), List.of()))).findFirst();
    }
}
