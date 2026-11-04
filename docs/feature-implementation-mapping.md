# Feature to implementation mapping

All twelve accepted Stage 1 features are implemented and published through shared Swing/CLI controller workflows. The student confirmed review/testing through Step 21; subsequent regression checks and fixes remain attributed separately. Model failure remains explicit PARTIAL/unavailable behavior. The demonstration index links the existing GUI/CLI recordings.

| Feature | Type | Key methods | Patterns | Status | Video |
| --- | --- | --- | --- | --- | --- |
| F01 Natural language planning | Hybrid | start, plan, createPlan, validate, acceptRulePlan | MVC, Strategy, Adapter | Implemented and published | [GUI/CLI recordings](demonstration-index.md) (00:00 to end) |
| F02 Validated dataset import | Deterministic | importLogs, importFile, ingest, recordImport | MVC | Implemented and published | [GUI/CLI recordings](demonstration-index.md) (00:00 to end) |
| F03 Filtered security log search | Deterministic | openSession, execute, query, search, addEvidence | MVC, Command | Implemented and published | [GUI/CLI recordings](demonstration-index.md) (00:00 to end) |
| F04 Threat indicator enrichment | Deterministic | loadIndicatorFeed, lookup, query, acceptEvidenceIndicators | MVC, Command | Implemented and published | [GUI/CLI recordings](demonstration-index.md) (00:00 to end) |
| F05 Account activity investigation | Deterministic | activity, execute, query, addEvidence | MVC, Command | Implemented and published | [GUI/CLI recordings](demonstration-index.md) (00:00 to end) |
| F06 Timeline correlation | Deterministic | events, build, correlate, analyze | MVC | Implemented and published | [GUI/CLI recordings](demonstration-index.md) (00:00 to end) |
| F07 Grounded hypothesis analysis | Hybrid | analyze, validateCitations, setAssessment | MVC, Adapter | Implemented and published | [GUI/CLI recordings](demonstration-index.md) (00:00 to end) |
| F08 Explainable severity | Hybrid | derive, assess, analyze | MVC, Strategy | Implemented and published | [GUI/CLI recordings](demonstration-index.md) (00:00 to end) |
| F09 Response recommendations | Hybrid | analyze, validateCitations, export | MVC, Adapter | Implemented and published | [GUI/CLI recordings](demonstration-index.md) (00:00 to end) |
| F10 Contextual follow up | Hybrid | ask, answer, createPlan, query, recordConversation | MVC, Strategy, Command, Adapter | Implemented and published | [GUI/CLI recordings](demonstration-index.md) (00:00 to end) |
| F11 Persistent cases and history | Deterministic | save, load, list, restore, snapshot, publish | MVC, Observer | Implemented and published | [GUI/CLI recordings](demonstration-index.md) (00:00 to end) |
| F12 Report export | Deterministic | preview, export, snapshot | MVC | Implemented and published | [GUI/CLI recordings](demonstration-index.md) (00:00 to end) |

## F01 Natural language planning

Stage 1: UC01 / SD01. AgentController creates/clarifies the case; AgentEngine delegates to PlanningStrategy; LLMPlanningStrategy and RulePlanningStrategy generate plans; PlanValidator checks tools, scopes, dependencies and limits.

The analyst supplies a goal, dataset, target and UTC interval. Invalid model drafts receive one repair. Missing scope leaves DRAFT, and basic rule fallback requires explicit approval on the same case. Planning never executes tools.

Invocation: New case or Generate model plan; CLI new / plan / rule. See [stage2-report.docx](stage2-report.docx) for the full file mapping and demonstration index.

## F02 Validated dataset import

Stage 1: UC02 / SD02. AgentController preflights state; LogImporter validates a bounded UTF-8 event array; DatasetStore publishes durable immutable versions; Investigation records version, audit and staleness.

Conflicting event IDs reject atomically; identical duplicates count once. New versions retain historical evidence, clear the old plan, invalidate sessions and stale analysis. Invalid input and failed durable writes preserve the previous dataset.

Invocation: Import JSON; CLI import. See [stage2-report.docx](stage2-report.docx) for the full file mapping and demonstration index.

## F03 Filtered security log search

Stage 1: UC03 / SD03. ToolExecutor checks a case-bound session and dispatches LogSearchCommand to DatasetStore; ResultVerifier checks canonical source payloads before Investigation attaches evidence.

The view submits string-valued arguments pinned to a dataset/version, target and interval. Results are ordered and deduplicated with provenance and audit references. Empty, capped and failed retrieval are distinct outcomes.

Invocation: logSearch query; CLI query --tool logSearch. See [stage2-report.docx](stage2-report.docx) for the full file mapping and demonstration index.

## F04 Threat indicator enrichment

Stage 1: UC03 / SD04. IndicatorFeedLoader validates source-dated records; IndicatorLookupCommand delegates to DatasetStore.lookup; AgentEngine accepts only the scoped target or indicators resolved from accepted events.

Normalized IP/domain lookup preserves selected feed version, verdict, source and observation date. UNKNOWN is not SAFE; stale context remains visible. Observed indicators can extend the visible plan without resetting budgets or duplicate history.

Invocation: indicatorLookup query; CLI query --tool indicatorLookup. See [stage2-report.docx](stage2-report.docx) for the full file mapping and demonstration index.

## F05 Account activity investigation

Stage 1: UC03 / SD03. UserActivityCommand reuses LocalQueryCommand; DatasetStore computes ordered events/counts; ActivitySummary records full interval and returned coverage; Investigation attaches unique results.

The shared retrieval path reports successes, failures, hosts and unknown values. Truncation retains total-versus-returned coverage; unknown accounts return empty results. Overlapping activity/search evidence attaches once.

Invocation: userActivity query; CLI query --tool userActivity. See [stage2-report.docx](stage2-report.docx) for the full file mapping and demonstration index.

## F06 Timeline correlation

Stage 1: UC04 / SD05. CaseEvidence resolves canonical in-scope source events; TimelineBuilder orders/deduplicates and produces relationships; AnalysisService includes the timeline in Assessment.

UTC order is timestamp then event ID. Shared account/host within 15 minutes creates candidate related pairs, not causation. Missing optional fields do not block ordering; source-ID conflicts reject the standalone builder. Output is capped at 1000 pairs.

Invocation: Timeline after Run; CLI run / exported report. See [stage2-report.docx](stage2-report.docx) for the full file mapping and demonstration index.

## F07 Grounded hypothesis analysis

Stage 1: UC04 / SD05. AnalysisService prompts through LLMGateway, resolves prompt-local refs to canonical UUIDs and validates structured synthesis; Investigation stores accepted Assessment values.

The model receives at most 24 canonical evidence items, fixed severity and prior hypotheses. Unknown citations, invalid schema or confidence receive one repair. Persistent failure keeps deterministic results as PARTIAL with AI analysis unavailable. Citation validity is not semantic proof.

Invocation: Hypotheses after Run; CLI run. See [stage2-report.docx](stage2-report.docx) for the full file mapping and demonstration index.

## F08 Explainable severity

Stage 1: UC04 / SD05. RuleSeverityPolicy derives Facts from source events and accepted feed context; SeverityPolicy fixes scoring; AnalysisService preserves that result independently of model assertions.

Each rule scores once: five failed logins in ten minutes +2; subsequent success within ten minutes +3; malicious login source +2; affected critical host +1. LOW 0-2, MEDIUM 3-5, HIGH 6-8; no usable authentication means UNDETERMINED. Unknown optional facts score zero and are disclosed.

Invocation: Assessment after Run; CLI run. See [stage2-report.docx](stage2-report.docx) for the full file mapping and demonstration index.

## F09 Response recommendations

Stage 1: UC04 / SD05. AnalysisService validates Recommendation fields, references and categories; Assessment stores accepted rationale, priority and prerequisites; ReportService formats without inference.

Only COLLECT, REVIEW and ESCALATE proposals are accepted. Every proposal cites evidence and is marked for analyst approval; no containment command exists. Invalid or unavailable synthesis produces no fabricated recommendation.

Invocation: Assessment recommendations; CLI run. See [stage2-report.docx](stage2-report.docx) for the full file mapping and demonstration index.

## F10 Contextual follow up

Stage 1: UC05 / SD06. AgentEngine optionally refreshes scoped tools through RulePlanningStrategy and ToolExecutor; AnalysisService answers; Investigation owns ConversationEntry history.

Questions use accepted evidence, fixed severity and prior hypotheses. A refresh: prefix requests bounded collection; scope widening requires an explicit revision. Answers resolve to existing UUIDs, with one repair for invalid citations. Failures are recorded and autosaved.

Invocation: Ask and Conversation; CLI ask. See [stage2-report.docx](stage2-report.docx) for the full file mapping and demonstration index.

## F11 Persistent cases and history

Stage 1: UC06-UC07 / SD07. JsonCaseRepository validates closed JSON records and case revisions; AtomicFiles replaces files atomically; DatasetStore restores retained sources/feed provenance; Investigation restores aggregate state.

Locks reject competing/stale case writes. Invalid schemas/references reject before restoration. RUNNING restores as PAUSED and needs revalidation. Unavailable source versions remain inspectable but block retrieval. Case/dataset files are separately atomic, not a cross-file transaction.

Invocation: Save and Open case; CLI save / open / list. See [stage2-report.docx](stage2-report.docx) for the full file mapping and demonstration index.

## F12 Report export

Stage 1: UC08 / SD08. ReportService formats immutable accepted case data; AtomicFiles protects output; AgentController exposes preview/export to both views.

Markdown includes scope, plan, provenance/payloads, timeline/relationships, severity, hypotheses, recommendations, conversation, failures, completeness and versions. Export makes no model calls. Existing destinations require GUI confirmation or explicit CLI --overwrite.

Invocation: Report and Export Markdown; CLI preview / export. See [stage2-report.docx](stage2-report.docx) for the full file mapping and demonstration index.

Step 14 publication implements the deterministic severity component of F08 through RuleSeverityPolicy.derive/assess and immutable Facts: accepted canonical source facts, R1–R4 weights 2/3/2/1, fixed LOW/MEDIUM/HIGH thresholds, UNDETERMINED without usable authentication, supporting source event IDs and explicit coverage limitations. Twelve focused checks and demos/severity-demo.jsh verify the component independently of model synthesis and later application integration.

Step 15 publication implements grounded analysis/recommendation components through AnalysisService.analyze, validateCitations, versioned synthesis records and JsonCodec. Trusted context comes from canonical accepted scope evidence, TimelineBuilder and fixed RuleSeverityPolicy. Supporting/contradicting references must belong to selected evidence; recommendations are restricted proposals with rationale/priority/prerequisites. One repair and one transient retry share ModelBudget. Invalid/unavailable output preserves deterministic PARTIAL results. Verified by 24 focused checks and demos/analysis-demo.jsh; orchestration, follow-up and interface integration remain later increments.

Step 16 publishes AgentEngine.run/cancel/inventory and AgentController.run/cancel/inventory with synchronized Investigation operations. The engine selects eligible Plan steps, reuses scoped Command execution, attaches source-verified evidence, performs bounded observed-indicator revisions and passes its retained planning ModelBudget into AnalysisService. Lifecycle/progress and deterministic PARTIAL/FAILED outcomes preserve evidence. Fifteen focused integration checks and demos/orchestration-demo.jsh verify this increment independently of later disk persistence, follow-up and presentation workflows.

Step 17 publishes JSON persistence/restoration through JsonCaseRepository.save/load/list, DatasetStore.save/restore/durable, AtomicFiles.read/write and strict JsonCodec record conversion. Repository revisions/locks/identity and dataset version-prefix/feed provenance validation protect accepted state. AgentEngine.revalidate and AgentController.resume require explicit readiness validation after RUNNING-to-PAUSED restoration. Eighteen focused integration checks plus two codec regressions and demos/persistence-demo.jsh verify this component. The recording-era long duration fix is included; later GUI query-result decoding inherits it.

Step 18 publishes AnalysisService.answer, AgentEngine.ask and Investigation.recordConversation with accepted canonical citations, a bounded repair, same-scope optional refresh and persisted answer/failure history. ReportService.preview/export renders accepted case state deterministically through AtomicFiles, including provenance, completeness and audit history, with explicit overwrite. Seventeen focused checks and demos/followup-report-demo.jsh verify these components independently of later CLI/Swing integration. Runtime Markdown reports are distinct from the assignment Word report.

Step 19 publishes Main CLI routing, ApplicationSession composition, ActionDispatcher shared actions, SentinelCLI.parse/submit/update, immutable Action/Result and AgentController helpers for explicit rule approval, listing/current/replan/revise/open progress. Existing F01–F12 services are exposed through shared controller operations with durable case/dataset selection, mutation saving, JSON envelopes, stderr progress and 0/2/3 exits. Five CLI/process/parser checks and demos/cli-workflow.py verify the increment; model-dependent fixtures use fake native Ollama HTTP. Swing launch and optional provider/setup changes remain separate.

Step 20 publishes SentinelGUI and Main.gui lifetime/EDT wiring. MVC view actions share ActionDispatcher/AgentController with CLI; SwingWorker and ProgressListener update accepted-case tabs on the EDT. Scope, collection/planning, run/stop, revalidation, persistence, follow-up and export controls use existing services. Four Swing checks verify dispatch, saving, progress and background rendering. The initial Evidence tab shows accumulated case evidence; recording-era query replacement/preflight behavior is a separate correction.

The separate Step 20 correction adds view-local latest-query evidence, preflight checks and typed tool progress while retaining aggregate evidence. Three display regressions pass; all 456 isolated tests passed.

Step 21 adds data/scenarios/account-takeover.json and benign.json, integrated CompletionWorkflowTest and explicit ScenarioCoverageTest, native Qwen diagnostics/startup and CLI replay. Together they verify the F01–F12 service collaborations across source provenance, planning/collection, fixed severity, cited analysis/follow-up, persistence/recovery, shared presentation dispatch and export. The original replay accepts PARTIAL status; the separate correction introduces strict replay success gates. Current model-planning/analysis timeout limitations are recorded separately from passing deterministic tests.

The separate Step 21 correction adds strict replay completeness/citation/persistence gates, explicit PowerShell array/stderr handling and portable isolated CLI replay. Four fake-server helper regressions passed; the Java 490-test tree is unchanged.
