# Design-pattern implementation record

The Stage 1 Command collaboration now works at component level through scoped ToolExecutor sessions, concrete commands and DatasetStore. The other patterns and complete application workflows remain pending.

| Pattern | Problem | Participants and roles | Status |
| --- | --- | --- | --- |
| MVC | Shared GUI/CLI behavior | SentinelGUI and SentinelCLI: views; AgentController: controller; Investigation and values: model | Pending |
| Strategy | Exchange AI and rule planning | AgentEngine: context; PlanningStrategy: contract; LLMPlanningStrategy and RulePlanningStrategy: strategies | Pending |
| Command | Encapsulate restricted retrieval | ToolExecutor: invoker; ToolCommand: contract; LocalQueryCommand and concrete queries; DatasetStore: receiver | Implemented component collaboration: ToolExecutor.Session validates and dispatches allow-listed commands through LocalQueryCommand to DatasetStore; 31 executor tests and demos/executor-demo.jsh verify scope, budgets and failures. Application integration pending |
| Observer | Progress without view coupling | EventBus: publisher; ProgressListener: contract; GUI/CLI: listeners | Pending |
| Adapter | Isolate model communication | LLMGateway: target; OllamaAdapter: adapter; Ollama: service | Pending |

The original five patterns remain unchanged. Implementation UML and source-method evidence will be added as each collaboration becomes functional. Current component progress is recorded in the feature mapping.
