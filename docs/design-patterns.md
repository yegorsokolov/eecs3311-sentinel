# Design-pattern implementation record

These are the planned Stage 1 roles. Domain lifecycle, import and log-search components now work, but none of the five complete pattern collaborations is demonstrated yet.

| Pattern | Problem | Participants and roles | Status |
| --- | --- | --- | --- |
| MVC | Shared GUI/CLI behavior | SentinelGUI and SentinelCLI: views; AgentController: controller; Investigation and values: model | Pending |
| Strategy | Exchange AI and rule planning | AgentEngine: context; PlanningStrategy: contract; LLMPlanningStrategy and RulePlanningStrategy: strategies | Pending |
| Command | Encapsulate restricted retrieval | ToolExecutor: invoker; ToolCommand: contract; LocalQueryCommand and concrete queries; DatasetStore: receiver | Partial: LogSearchCommand executes through LocalQueryCommand; validated executor/registry integration pending |
| Observer | Progress without view coupling | EventBus: publisher; ProgressListener: contract; GUI/CLI: listeners | Pending |
| Adapter | Isolate model communication | LLMGateway: target; OllamaAdapter: adapter; Ollama: service | Pending |

The original five patterns remain unchanged. Implementation UML and source-method evidence will be added as each collaboration becomes functional. Current component progress is recorded in the feature mapping.
