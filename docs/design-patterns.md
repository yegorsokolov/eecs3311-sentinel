# Design-pattern implementation record

The Stage 1 Command collaboration now works at component level through scoped ToolExecutor sessions, concrete commands and DatasetStore. Step 9 integrates it with the controller/model and adds immutable progress dispatch; complete user-facing workflows remain pending.

| Pattern | Problem | Participants and roles | Status |
| --- | --- | --- | --- |
| MVC | Shared GUI/CLI behavior | SentinelGUI and SentinelCLI: views; AgentController: controller; Investigation and values: model | Controller/model import and retrieval integration implemented; views pending |
| Strategy | Exchange AI and rule planning | AgentEngine: context; PlanningStrategy: contract; LLMPlanningStrategy and RulePlanningStrategy: strategies | Pending |
| Command | Encapsulate restricted retrieval | ToolExecutor: invoker; ToolCommand: contract; LocalQueryCommand and concrete queries; DatasetStore: receiver | Implemented component collaboration: ToolExecutor.Session validates and dispatches allow-listed commands through LocalQueryCommand to DatasetStore; 31 executor tests and demos/executor-demo.jsh verify scope, budgets and failures. Application integration implemented through source validation, evidence attachment and audit updates |
| Observer | Progress without view coupling | EventBus: publisher; ProgressListener: contract; GUI/CLI: listeners | Immutable progress dispatch and listener failure isolation implemented; views pending |
| Adapter | Isolate model communication | LLMGateway: target; OllamaAdapter: adapter; Ollama: service | Pending |

The original five patterns remain unchanged. Implementation UML and source-method evidence will be added as each collaboration becomes functional. Current component progress is recorded in the feature mapping.
