# Sentinel

Sentinel is a local cybersecurity incident triage and investigation application developed for EECS 3311 Software Design. It provides a Swing GUI and a command-line interface for scoped evidence collection, incident reconstruction and analyst review.

Qwen proposes collection plans and evidence-grounded explanations. Sentinel validates proposed tools and scope, computes severity using fixed rules, retains source provenance and saves investigation history. Recommendations are proposals for human review; the application does not execute containment actions.

## Documentation

- [GUI user guide](docs/gui-user-guide.md): fields, buttons, tabs, workflows and troubleshooting.
- [CLI user guide](docs/cli-user-guide.md): command reference, copyable examples, JSON output and exit codes.
- [Model setup](docs/model-setup.md): installation, configuration and provider options.
- [Verification guide](docs/reviewer-verification.md): reproduction steps and verification scope.
- [Implementation architecture](docs/implementation-architecture.md) and [design patterns](docs/design-patterns.md).
- [Feature mapping](docs/feature-implementation-mapping.md) and [Stage 2 report](docs/stage2-report.pdf).
- [Verification results](docs/verification-log.md), [AI collaboration record](docs/ai-collaboration-log.md) and [AI tools and costs](docs/ai-tools-and-costs.md).

## Quick start on Windows

Install Python 3.10+ and a full JDK 17+, then open PowerShell in the project root:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
.\scripts\setup-qwen.ps1 --launch
```

Adjust JAVA_HOME to your JDK installation. Setup installs or reuses portable Ollama, downloads Qwen when needed, starts and warms the model, configures `.env`, builds/tests Sentinel and verifies real completion and validated planning before opening the GUI. Allow time for the first download and cold model load. Rerun setup after reboot to reuse the installation and restart the server.

Omit `--launch` for CLI-only setup. No Python packages are required; `pip install -r requirements.txt` does not install native prerequisites or start the model. See [requirements.txt](requirements.txt) and the [setup guide](docs/model-setup.md).

To open the GUI after setup:

```powershell
& "$env:JAVA_HOME\bin\java.exe" -jar '.\target\sentinel-0.1.0-SNAPSHOT.jar' gui --dataset analyst_demo
```

Run from the project root. The dataset defaults to `local` when omitted. Use the same dataset and storage configuration when reopening an existing investigation.

## Linux setup

Install Python 3.10+, JDK 17+, `tar` and `zstd`, then run from the project root:

```bash
bash scripts/setup-qwen.sh
java -jar target/sentinel-0.1.0-SNAPSHOT.jar gui --dataset analyst_demo
```

The GUI requires a graphical desktop. On a headless machine, use the CLI after setup. Native Ubuntu 24.04.5 x86-64 CPU setup and CLI execution have been verified; Linux GUI, ARM64 and GPU execution have not been verified. See [model setup](docs/model-setup.md) for platform details.

## Investigation workflow

1. Create a case with an investigation goal, target account or indicator, UTC interval and feed version. Model planning is the default; basic rule planning requires explicit approval.
2. Import a supplied event file, such as `data/scenarios/account-takeover.json`. New events advance the dataset version and invalidate the previous plan.
3. Query scoped events with `logSearch` or `userActivity`, and enrich observed indicators with `indicatorLookup`.
4. Generate or approve a plan for the current dataset version, inspect it, then Run.
5. Review the timeline, hypotheses, fixed severity, recommendations, completeness and evidence references. Ask a follow-up when needed.
6. Save, reopen by case UUID, or export a Markdown report.

In the GUI, each manual Query replaces the Evidence display with that query's returned records. A search with limit 6 followed by limit 2 therefore shows six records, then two. Accepted evidence remains accumulated in the saved case and report. Empty results and failed queries are distinguished by the progress outcome. The GUI currently has no dedicated activity-count panel; use the CLI's typed activity summary for counts.

Planning does not execute collection tools. READY means a validated plan is available; Run performs collection and analysis. A PARTIAL result or STALE assessment retains its limitations and must not be presented as complete. Stop requests cooperative cancellation after the current run operation.

## CLI

Each command starts a new Java process. There is no installed `sentinel` executable or interactive Sentinel shell.

```powershell
& "$env:JAVA_HOME\bin\java.exe" -jar '.\target\sentinel-0.1.0-SNAPSHOT.jar' --help
.\demos\cli-scenario.ps1 -DatasetId analyst_cli_demo
```

The scenario script uses supplied synthetic data, creates a case, imports events, runs analysis, asks a cited follow-up and exports a report. Build Sentinel and start Qwen first. Choose a new dataset ID for an independent run.

Commands include `new`, `import`, `query`, `plan`, `rule`, `clarify`, `revise`, `run`, `ask`, `resume`, `save`, `open`, `list`, `show`, `preview` and `export`. Existing-case operations use `--case UUID`. Successful mutations save automatically.

Stdout contains a JSON Result envelope; its `message` usually contains an encoded JSON payload. Progress goes to stderr. Exit codes are 0 for successful dispatch, 2 for invalid input/rejection and 3 for operational failure. Inspect the payload and progress as well: a successful dispatch may contain a PARTIAL result or a DRAFT case with blocked planning. See the [CLI guide](docs/cli-user-guide.md) for decoding examples and full syntax.

## Build and verification

The Maven Wrapper downloads the pinned Maven distribution and dependencies on first use; no global Maven installation is required.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
.\mvnw.cmd --batch-mode verify
& "$env:JAVA_HOME\bin\java.exe" -jar '.\target\sentinel-0.1.0-SNAPSHOT.jar' --check-config
```

On Linux/macOS use `sh mvnw --batch-mode verify`. The packaged application is `target/sentinel-0.1.0-SNAPSHOT.jar`; test reports are under `target/surefire-reports/`. Configuration diagnostics do not contact the model.

The latest verified Java build passed 405 tests with no failures, errors or skips. Automated tests use local fixtures and fake providers rather than requiring live Qwen. Separate real Qwen checks have verified planning, a synthetic COMPLETE / 7 HIGH investigation, cited follow-up, report export and reopening. These are smoke checks, not a broad model-quality evaluation. See the verification record for the scope and limits of individual checks.

For native Linux reproduction, including setup-helper checks and real Qwen integration:

```bash
bash scripts/test-linux.sh
```

This downloads/starts the local model when needed and writes synthetic runtime data. See the [verification guide](docs/reviewer-verification.md) for prerequisites and measured results. Full KUMA evaluation remains outside the completed verification scope.

## Configuration and storage

Configuration precedence is bundled defaults, then local `.env`, then process environment. Setup creates the ignored `.env`; local Ollama needs no API key. Optional hosted OpenAI-compatible providers are configured separately as described in [model setup](docs/model-setup.md).

Default storage is:

| Location | Contents |
| --- | --- |
| `runtime/datasets` | Durable datasets and retained source versions. |
| `runtime/cases` | Case snapshots, accepted evidence, conversation and history. |
| `runtime/exports` | Exported Markdown reports. |
| `runtime/ollama` | Portable local model runtime and model downloads. |

Storage paths can be changed with `SENTINEL_DATA_DIR`, `SENTINEL_CASE_DIR` and `SENTINEL_EXPORT_DIR`. Relative paths resolve from the working directory. Keep source datasets alongside case backups; a case snapshot does not replace its source store. Use one writer per dataset and avoid simultaneous GUI/CLI changes to the same case. A saved RUNNING case restores as PAUSED and requires explicit plan revalidation before Run.

Imports accept UTF-8 JSON arrays up to 10 MiB and 100,000 events. Required string fields are `eventId`, `timestamp` and `eventType`; timestamps need a UTC offset. Invalid or conflicting batches are rejected atomically. Identical records count as duplicates. The local sample indicator feed loads from `data/indicators/threat-feed.json` when present; verdict freshness depends on its observation date.

Execution defaults bound a run to eight tool attempts, two plan revisions and six model calls, with ten-second tool and sixty-second model request deadlines. Analysis model context is capped at 24 evidence items and candidate relationships at 1,000 pairs. Limits, unavailable AI and missing data remain visible in results.

## Architecture

The GUI and CLI share ActionDispatcher and AgentController. AgentEngine coordinates validated planning, scoped tool execution and analysis; Investigation owns the case lifecycle. LLMGateway isolates model providers, DatasetStore retains canonical source versions, JsonCaseRepository persists snapshots, and ReportService exports accepted values without new inference.

The implementation uses MVC, Strategy, Command, Observer and Adapter. See [implementation architecture](docs/implementation-architecture.md), [design patterns](docs/design-patterns.md) and the [implemented class model](docs/uml/implemented-system.puml).

## Step 14: deterministic severity policy

RuleSeverityPolicy derives facts only from accepted canonical evidence in the current case scope. Policy version 1 applies each rule once: R1 adds 2 for at least five failed logins for the same account within an inclusive 10-minute window; R2 adds 3 for a success strictly after a qualifying burst and within the following 10 minutes; R3 adds 2 for a login IP matched by accepted canonical MALICIOUS evidence from the selected feed; R4 adds 1 for an affected in-scope host explicitly listed as critical. Facts retains supporting source event IDs and immutable inputs.

Scores 0–2 are LOW, 3–5 MEDIUM and 6–8 HIGH. Without usable authentication evidence the result is UNDETERMINED. Missing inventory/feed and partial coverage produce explicit limitations; missing evidence is not a safety finding. Model text cannot change this policy. Full analysis/orchestration integration remains outside this publication.

The isolated Step 14 snapshot passes 368 tests, including 12 focused severity checks. After building, run the synthetic component demo:

```powershell
& "$env:JAVA_HOME\bin\jshell.exe" --class-path target/sentinel-0.1.0-SNAPSHOT.jar demos/severity-demo.jsh
```

Expected: score 5 MEDIUM, rules R1/R2, contributing event IDs, and missing inventory/feed limitations. No model inference or case/dataset disk mutation is performed.

## Step 15: grounded model analysis and recommendations

AnalysisService.analyze combines the deterministic canonical timeline and fixed severity with a bounded synthesis request. The analysis-v1 contract accepts compact prompt-local E1 references or the full Synthesis record; references resolve to accepted immutable evidence UUIDs before validation. Hypotheses distinguish support, contradiction, qualitative confidence and limitations. Recommendations accept only COLLECT, REVIEW or ESCALATE and retain rationale, priority, source citations and prerequisites for analyst approval.

At most 24 canonical in-scope evidence items enter model context; the full timeline remains available, while capped coverage is explicitly PARTIAL. One schema/citation repair and one transient retry share the caller's ModelBudget. Malformed, unavailable or budget-exhausted output preserves timeline/severity with an audit record and explicit AI-unavailable limitation. Model output cannot change severity or execute a recommendation. Citation membership is checked; it does not establish semantic correctness or causation.

The isolated publication snapshot passes 392 tests, including 24 focused grounded-analysis checks. After building, run the deterministic synthetic gateway walkthrough:

```powershell
& "$env:JAVA_HOME\bin\jshell.exe" --class-path target/sentinel-0.1.0-SNAPSHOT.jar demos/analysis-demo.jsh
```

Expected: one repaired COMPLETE synthesis in two calls, unchanged score 5 MEDIUM, citations resolved to canonical evidence and a recommendation requiring analyst approval. This fixture performs no real model inference. Follow-up questions, autonomous orchestration and later persistence/interfaces remain separate milestones.

## Step 16: bounded investigation orchestration

AgentEngine.run independently validates a READY plan, enters RUNNING, selects dependency-eligible calls and reuses one scoped ToolExecutor session for collection and observation-driven indicator enrichment. Plan revisions stop at 2, tool attempts at 8 and shared planning/analysis model attempts at 6; retries consume the same budgets. Evidence authorization preserves session attempt and duplicate history. Investigation synchronizes aggregate mutation and snapshots; EventBus publishes progress without coupling the engine to a view.

Successful grounded analysis ends COMPLETED. Truncation, tool/provider failure, exhausted budgets or cooperative cancellation preserve accepted evidence and deterministic results as PARTIAL; unexpected failures after starting a run enter FAILED. Cancellation and interruption stop after the current bounded operation and prevent further model requests. Cancellation during analysis retains a deterministic PARTIAL outcome. AgentController.run releases its monitor while work executes, allowing cancel to remain available. A rejected non-READY run preserves any prior planning budget.

The isolated Step 16 publication snapshot passes 407 tests, including 15 focused orchestration checks. After building, run:

```powershell
& "$env:JAVA_HOME\bin\jshell.exe" --class-path target/sentinel-0.1.0-SNAPSHOT.jar demos/orchestration-demo.jsh
```

Expected: progress through RUNNING to COMPLETED, synthetic score 7 HIGH, six timeline events, seven evidence items, plan revision 1 and one fake model call. No real inference or case/dataset disk writes occur. Disk restoration/revalidation, follow-up questions and user-interface workflows remain later increments.
