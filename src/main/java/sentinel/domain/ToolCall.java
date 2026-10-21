package sentinel.domain;

import java.util.Map;
import java.util.UUID;

/** Immutable tool request. Registry/argument validation belongs to PlanValidator/ToolExecutor. */
public record ToolCall(UUID id, String toolName, Map<String, String> args) {
    public ToolCall {
        DomainValidation.required(id, "id");
        DomainValidation.requiredText(toolName, "toolName");
        args = Map.copyOf(DomainValidation.required(args, "args"));
    }

    public String key() {
        throw new UnsupportedOperationException("Canonical tool keys are implemented with tool execution.");
    }
}
