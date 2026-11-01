# Implemented design patterns

The accepted five patterns now collaborate in the connected application. No extra pattern is claimed for routing or serialization helpers.

| Pattern | Problem and roles | Working collaboration |
| --- | --- | --- |
| MVC | Both views need the same behavior; SentinelGUI/CLI are views, AgentController coordinates, Investigation is model | ActionDispatcher routes Actions to shared start/import/query/run/ask/save/open/export services. SwingWorker performs service work off the EDT. |
| Strategy | Planning and severity vary behind ports | AgentEngine delegates to PlanningStrategy (LLMPlanningStrategy or explicitly approved RulePlanningStrategy), then independently validates output. AnalysisService invokes SeverityPolicy with deterministic Facts. |
| Command | Model/tool requests must be restricted | ToolExecutor.Session validates and invokes ToolCommand; LogSearchCommand, UserActivityCommand and IndicatorLookupCommand extend LocalQueryCommand; DatasetStore is receiver. Engine validates canonical output before attachment. |
| Observer | Services must report progress independently of views | EventBus publishes immutable ProgressEvent to ProgressListener; CLI writes stderr, Swing queues updates to the EDT. Failed subscribers cannot undo accepted mutations. |
| Adapter | Model protocol must stay outside application/domain code | LLMGateway is target; OllamaAdapter and optional OpenAICompatibleAdapter translate bounded HTTP and typed failures. ModelGatewayFactory selects the implementation in composition. |

These preserve Stage 1. Updated class views and editable UML are under `docs/uml/`; current service details are in `implementation-architecture.md`. The latest Java build passed 405 tests. Fake-server checks validate transport and failure behavior; actual Qwen planning, synthesis and follow-up have separate recorded smoke evidence, not a broad quality guarantee.
