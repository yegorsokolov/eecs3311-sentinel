package sentinel.domain;

/**
 * An analyst goal with an optional complete scope.
 * A null scope represents a request awaiting clarification (Stage 1 F01/UC01);
 * it must not be used to execute tools. The planning workflow enforces that guard.
 */
public record Request(String goal, CaseScope scope) {
    public Request {
        DomainValidation.requiredText(goal, "goal");
    }
}
