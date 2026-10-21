package sentinel.domain;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Immutable structurally valid plan; semantic validation is the planning service's responsibility. */
public record Plan(UUID id, int revision, List<ToolCall> steps) {
    public Plan {
        DomainValidation.required(id, "id");
        if (revision < 0) throw new IllegalArgumentException("revision must not be negative.");
        steps = List.copyOf(DomainValidation.required(steps, "steps"));
        if (steps.isEmpty() || steps.size() > 8) {
            throw new IllegalArgumentException("A plan must have 1 to 8 steps.");
        }
        if (steps.stream().map(ToolCall::id).distinct().count() != steps.size()) {
            throw new IllegalArgumentException("Plan step IDs must be unique.");
        }
    }

    public Optional<ToolCall> next(Set<UUID> done) {
        throw new UnsupportedOperationException("Plan scheduling is implemented with planning.");
    }
}
