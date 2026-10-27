# Feature-to-implementation mapping

All entries refer to original Stage 1 feature IDs. No full user-facing feature is complete yet; partial component progress is recorded below. Method signatures alone do not establish functionality. Record actual collaborations, verification evidence and final video timestamps as implementation proceeds.

| ID | Feature | Type | Planned classes | Methods | Patterns | Status | Demo |
| --- | --- | --- | --- | --- | --- | --- | --- |
| F01 | Investigation planning | Hybrid | AgentController, AgentEngine, PlanningStrategy, LLMPlanningStrategy, RulePlanningStrategy, PlanValidator | start, plan, createPlan, validate | MVC, Strategy, Adapter | Partial: request/scope and lifecycle foundation; planning pending | Pending |
| F02 | Dataset import | Deterministic | LogImporter, DatasetStore, Investigation | importLogs, importFile, ingest, record | MVC (wiring pending) | Partial: importer/store implemented; active-case/UI wiring pending | Component demo: demos/import-demo.jsh; video pending |
| F03 | Security log search | Deterministic | ToolExecutor, LogSearchCommand, DatasetStore | execute, select, search | MVC, Command | Partial: filtered versioned search and command implemented; executor/case/UI wiring pending | Component demo: demos/search-demo.jsh; video pending |
| F04 | Indicator enrichment | Deterministic | IndicatorLookupCommand, DatasetStore | select, lookup | MVC, Command | Partial: strict local versioned feed loading, normalized matching, source evidence and unknown/stale/failure semantics; executor/case/UI wiring pending | Component demo: demos/indicator-demo.jsh; video pending |
| F05 | Account activity | Deterministic | UserActivityCommand, DatasetStore | select, activity | MVC, Command | Partial: ordered versioned account events, counts and coverage implemented; executor/case/UI wiring pending | Component demo: demos/activity-demo.jsh; video pending |
| F06 | Timeline correlation | Deterministic | AnalysisService, TimelineBuilder | analyze, build | MVC | Scaffold only | Pending |
| F07 | Hypothesis analysis | Hybrid | AnalysisService, LLMGateway, OllamaAdapter | analyze, complete, validateCitations | MVC, Adapter | Scaffold only | Pending |
| F08 | Severity assessment | Hybrid | RuleSeverityPolicy, AnalysisService | assess, analyze | MVC, Strategy | Scaffold only | Pending |
| F09 | Recommendations | Hybrid | AnalysisService, Assessment | analyze | MVC, Adapter | Scaffold only | Pending |
| F10 | Follow-up investigation | Hybrid | AgentEngine, AnalysisService, PlanningStrategy, ToolExecutor | ask, answer | MVC, Strategy, Command, Adapter | Scaffold only | Pending |
| F11 | Case persistence | Deterministic | Investigation, CaseRepository, JsonCaseRepository, EventBus | snapshot, save, load, publish | MVC, Observer | Partial: immutable snapshots/in-memory restoration; file persistence pending | Pending |
| F12 | Report export | Deterministic | AgentController, ReportService | export | MVC | Scaffold only | Pending |

## Source-file index

Step 7 implements F04 retrieval: IndicatorLookupCommand validates through IndicatorLookupQuery before calling DatasetStore.lookup. IndicatorFeedLoader parses a bounded strict UTF-8 versioned feed before atomic publication; Indicator and IndicatorRecord validate normalized source-dated records. IndicatorEvidence provides stable source-linked JSON evidence; optional IndicatorMatch data on ToolResult distinguishes matched UNKNOWN verdicts from absent records and preserves stale verdicts/dates. The five- and six-argument ToolResult constructors remain available. Forty-one tests cover normalized IP/domain matching, validation, atomic rejection/version retention, provenance, freshness and failures. The default 30-day age threshold and explicit loadIndicatorFeed entry point are implementation refinements; no feature or pattern is replaced. Executor/case/UI integration and durable storage remain pending.

Step 6 implements the F05 retrieval component: UserActivityCommand delegates through LocalQueryCommand to DatasetStore.activity, with shared strict query parsing and typed ActivitySummary data on ToolResult. Results include ordered source-linked events, full-interval outcome/host counts and explicit total/returned coverage when capped. Unknown accounts return EMPTY with zero counts; unknown outcomes/hosts remain unknown. The optional summary field refines the Stage 1 result-data contract while retaining the existing five-argument constructor. Twenty-four tests cover retrieval, boundaries, validation, version stability, provenance, counts and unknown/partial semantics. Case/executor/UI integration remains pending.

Step 5 implements the F03 retrieval component: LogSearchCommand delegates through LocalQueryCommand to DatasetStore.search. LogSearchQuery validates arguments; EventEvidence serializes source-linked events; ToolException distinguishes invalid arguments, missing datasets and missing versions. ToolResult defensively copies evidence. Tests cover inclusive intervals, exact filters, stable ordering/IDs, truncation, version pinning and errors. The 1000-result maximum and inclusive interval are implementation choices. ToolExecutor scope enforcement, evidence attachment and user-facing integration remain pending.


Step 4 implements F02/UC02/SD02 components: LogImporter.importFile performs bounded UTF-8 decoding, JSON schema/type checks and UTC normalization, then calls DatasetStore.ingest once. The store checks IDs against the staged/current events and atomically publishes an immutable version only when new events are accepted. Summary reports version, new/duplicate counts and safe errors. Controller invocation, case audit updates, stale assessments and durable datasets remain pending. LogImporterTest provides 31 checks; the packaged-JAR demo verifies the sample flow.

Step 3 provides partial F01/F11 infrastructure in Investigation (construction, clarifyScope, reviseScope, setPlan, record, setAssessment, snapshot, restore), CaseSnapshot and immutable nested values. It guards lifecycle transitions, preserves goal/metadata/history and pauses restored running cases. It does not complete planning, evidence ingestion, analysis, GUI/CLI workflows or disk persistence. InvestigationTest verifies these aggregate boundaries; the application must supply semantically validated plans and assessments.

Step 2 implements foundational contracts in `domain/CaseScope.java`, `Request.java`, `Event.java`, and `ImportBatch.java`, supported by package-private `DomainValidation.java` (all beneath `src/main/java/sentinel/`). They support F01/F02 and later retrieval/analysis, but do not complete those features. Constructors reject invalid mandatory fields; Request can await scope clarification; ImportBatch copies its list. CoreDomainTest covers these boundaries. Other domain records remain scaffolds.

Step 1 supporting infrastructure is implemented in `src/main/java/sentinel/config/AppConfig.java`, `ConfigurationException.java`, and `sentinel/Main.java`. It loads validated defaults/local overrides and provides --check-config, without invoking the model or changing case data. This is startup support, not a new meaningful feature or completion of F01-F12. Covered by AppConfigTest and MainTest; build and diagnostic commands are in the project README.

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
