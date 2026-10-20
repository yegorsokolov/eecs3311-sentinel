# Design-pattern implementation record

These are Stage 1 roles; working pattern behavior remains pending.

| Pattern | Problem | Participants and roles | Status |
| --- | --- | --- | --- |
| MVC | Shared GUI/CLI behavior | SentinelGUI and SentinelCLI: views; AgentController: controller; Investigation and values: model | Pending |
| Strategy | Exchange AI and rule planning | AgentEngine: context; PlanningStrategy: contract; LLMPlanningStrategy and RulePlanningStrategy: strategies | Pending |
| Command | Encapsulate restricted retrieval | ToolExecutor: invoker; ToolCommand: contract; LocalQueryCommand and concrete queries; DatasetStore: receiver | Pending |
| Observer | Progress without view coupling | EventBus: publisher; ProgressListener: contract; GUI/CLI: listeners | Pending |
| Adapter | Isolate model communication | LLMGateway: target; OllamaAdapter: adapter; Ollama: service | Pending |

TODO: Add relevant source methods and evidence of functioning collaborations. Preserve the original five patterns. Store implementation UML in `docs/uml/`.
