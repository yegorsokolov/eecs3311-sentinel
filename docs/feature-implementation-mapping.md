# Feature-to-implementation mapping

All entries refer to original Stage 1 feature IDs. Every feature is **not implemented - scaffold only**. Method signatures do not establish functionality. Add actual class responsibilities, collaboration flows, use-case references, verification evidence and demo timestamps during implementation.

| ID | Feature | Type | Planned classes | Methods | Patterns | Status | Demo |
| --- | --- | --- | --- | --- | --- | --- | --- |
| F01 | Investigation planning | Hybrid | AgentController, AgentEngine, PlanningStrategy, LLMPlanningStrategy, RulePlanningStrategy, PlanValidator | start, plan, createPlan, validate | MVC, Strategy, Adapter | Scaffold only | Pending |
| F02 | Dataset import | Deterministic | LogImporter, DatasetStore, Investigation | importLogs, importFile, ingest, record | MVC | Scaffold only | Pending |
| F03 | Security log search | Deterministic | ToolExecutor, LogSearchCommand, DatasetStore | execute, select, search | MVC, Command | Scaffold only | Pending |
| F04 | Indicator enrichment | Deterministic | IndicatorLookupCommand, DatasetStore | select, lookup | MVC, Command | Scaffold only | Pending |
| F05 | Account activity | Deterministic | UserActivityCommand, DatasetStore | select, activity | MVC, Command | Scaffold only | Pending |
| F06 | Timeline correlation | Deterministic | AnalysisService, TimelineBuilder | analyze, build | MVC | Scaffold only | Pending |
| F07 | Hypothesis analysis | Hybrid | AnalysisService, LLMGateway, OllamaAdapter | analyze, complete, validateCitations | MVC, Adapter | Scaffold only | Pending |
| F08 | Severity assessment | Hybrid | RuleSeverityPolicy, AnalysisService | assess, analyze | MVC, Strategy | Scaffold only | Pending |
| F09 | Recommendations | Hybrid | AnalysisService, Assessment | analyze | MVC, Adapter | Scaffold only | Pending |
| F10 | Follow-up investigation | Hybrid | AgentEngine, AnalysisService, PlanningStrategy, ToolExecutor | ask, answer | MVC, Strategy, Command, Adapter | Scaffold only | Pending |
| F11 | Case persistence | Deterministic | Investigation, CaseRepository, JsonCaseRepository, EventBus | snapshot, save, load, publish | MVC, Observer | Scaffold only | Pending |
| F12 | Report export | Deterministic | AgentController, ReportService | export | MVC | Scaffold only | Pending |

## Source-file index

- `src/main/java/sentinel/Main.java`
- `src/main/java/sentinel/analysis/AnalysisService.java`
- `src/main/java/sentinel/analysis/RuleSeverityPolicy.java`
- `src/main/java/sentinel/analysis/SeverityPolicy.java`
- `src/main/java/sentinel/analysis/TimelineBuilder.java`
- `src/main/java/sentinel/application/AgentController.java`
- `src/main/java/sentinel/application/AgentEngine.java`
- `src/main/java/sentinel/config/AppConfig.java`
- `src/main/java/sentinel/data/DatasetStore.java`
- `src/main/java/sentinel/data/LogImporter.java`
- `src/main/java/sentinel/domain/Action.java`
- `src/main/java/sentinel/domain/Answer.java`
- `src/main/java/sentinel/domain/Assessment.java`
- `src/main/java/sentinel/domain/AuditEntry.java`
- `src/main/java/sentinel/domain/CaseContext.java`
- `src/main/java/sentinel/domain/CaseScope.java`
- `src/main/java/sentinel/domain/CaseSnapshot.java`
- `src/main/java/sentinel/domain/CaseStatus.java`
- `src/main/java/sentinel/domain/CaseSummary.java`
- `src/main/java/sentinel/domain/Completeness.java`
- `src/main/java/sentinel/domain/ConversationEntry.java`
- `src/main/java/sentinel/domain/Event.java`
- `src/main/java/sentinel/domain/Evidence.java`
- `src/main/java/sentinel/domain/Facts.java`
- `src/main/java/sentinel/domain/Hypothesis.java`
- `src/main/java/sentinel/domain/ImportBatch.java`
- `src/main/java/sentinel/domain/IndicatorRecord.java`
- `src/main/java/sentinel/domain/Investigation.java`
- `src/main/java/sentinel/domain/Plan.java`
- `src/main/java/sentinel/domain/ProgressEvent.java`
- `src/main/java/sentinel/domain/Prompt.java`
- `src/main/java/sentinel/domain/Request.java`
- `src/main/java/sentinel/domain/Result.java`
- `src/main/java/sentinel/domain/Severity.java`
- `src/main/java/sentinel/domain/SeverityLevel.java`
- `src/main/java/sentinel/domain/Summary.java`
- `src/main/java/sentinel/domain/ToolCall.java`
- `src/main/java/sentinel/domain/ToolResult.java`
- `src/main/java/sentinel/events/EventBus.java`
- `src/main/java/sentinel/events/ProgressListener.java`
- `src/main/java/sentinel/llm/LLMGateway.java`
- `src/main/java/sentinel/llm/OllamaAdapter.java`
- `src/main/java/sentinel/persistence/CaseRepository.java`
- `src/main/java/sentinel/persistence/JsonCaseRepository.java`
- `src/main/java/sentinel/planning/LLMPlanningStrategy.java`
- `src/main/java/sentinel/planning/PlanValidator.java`
- `src/main/java/sentinel/planning/PlanningStrategy.java`
- `src/main/java/sentinel/planning/RulePlanningStrategy.java`
- `src/main/java/sentinel/presentation/SentinelCLI.java`
- `src/main/java/sentinel/presentation/SentinelGUI.java`
- `src/main/java/sentinel/reporting/ReportService.java`
- `src/main/java/sentinel/tools/IndicatorLookupCommand.java`
- `src/main/java/sentinel/tools/LocalQueryCommand.java`
- `src/main/java/sentinel/tools/LogSearchCommand.java`
- `src/main/java/sentinel/tools/ToolCommand.java`
- `src/main/java/sentinel/tools/ToolExecutor.java`
- `src/main/java/sentinel/tools/UserActivityCommand.java`
