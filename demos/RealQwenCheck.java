import sentinel.config.*;
import sentinel.data.*;
import sentinel.domain.*;
import sentinel.llm.*;
import sentinel.planning.*;
import sentinel.tools.*;
import java.time.*;

/** Opt-in check requiring real Ollama and the configured model; failures exit nonzero. */
public class RealQwenCheck {
    public static void main(String[] args) {
        var config = AppConfig.load();
        var adapter = new OllamaAdapter(config.ollamaUrl(), config.ollamaModel());
        long started = System.nanoTime();
        String reply = adapter.complete(new Prompt("diagnostic-v1", "diagnostic",
                "Return exactly one JSON object with a status field whose value is ok."));
        if (!reply.matches("(?s)\\s*\\{\\s*\"status\"\\s*:\\s*\"ok\"\\s*}\\s*"))
            throw new AssertionError("Unexpected diagnostic JSON");
        System.out.printf("REAL completion PASS: model=%s seconds=%.2f%n",
                adapter.modelVersion(), (System.nanoTime() - started) / 1e9);
        var store = new DatasetStore("demo");
        try (var tools = ToolExecutor.local(store)) {
            var planner = new LLMPlanningStrategy(adapter, new PlanValidator(tools, store));
            var scope = new CaseScope("demo", "v0", "demo-analyst",
                    Instant.parse("2026-10-22T12:00:00Z"), Instant.parse("2026-10-22T12:05:00Z"));
            var investigation = new Investigation(new Request("Investigate demo analyst", scope));
            var budget = new ModelBudget();
            started = System.nanoTime();
            var plan = planner.createPlan(new CaseContext(investigation.snapshot()), budget);
            if (plan.origin() != Plan.Origin.MODEL || !store.events("v0").isEmpty()
                    || !investigation.snapshot().evidence().isEmpty())
                throw new AssertionError("Unexpected origin or evidence mutation");
            System.out.printf("REAL planning PASS: origin=%s steps=%d requests=%d seconds=%.2f%n",
                    plan.origin(), plan.steps().size(), budget.used(), (System.nanoTime() - started) / 1e9);
            plan.steps().forEach(step -> System.out.println("Validated step: " + step));
        }
    }
}
