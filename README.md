# Sentinel

AI cybersecurity incident triage and investigation agent for EECS 3311 Software Design.

## Current status

Steps 1-9 implement build/configuration diagnostics, core contracts, the in-memory case lifecycle/snapshots, and validated JSON import into an atomic versioned dataset store, plus filtered log-search, account-activity and local indicator-lookup commands and scoped, bounded tool execution sessions. Step 9 connects active-case import and retrieval, source-verified evidence attachment, deduplication, audit history and assessment staleness. F01-F12 are not yet complete as user-facing workflows. GUI, investigation CLI commands, model integration, plan validation, investigation orchestration and JSON file persistence remain pending.

## Design

Java with a Swing GUI and CLI sharing `AgentController`; bounded orchestration through `AgentEngine`; local Qwen2.5 7B Instruct through Ollama; JSON snapshots and local datasets. Planned patterns: MVC, Strategy, Command, Observer and Adapter. The existing report and `UML Diagrams/` remain the design baseline.

No Stage 1 features, architecture choices or design patterns have been replaced. Implementation refinements include build/configuration support and restoring the original analyst goal omitted from the initial snapshot scaffold. These support the accepted design.

## Prerequisites and build

- JDK 17 or newer; source compatibility targets Java 17.
- The included official Maven Wrapper downloads Maven 3.9.9 on first use; no global Maven install is needed. First use requires internet access for Maven and dependencies.
- Ollama and its model will be needed when integration is implemented. The skeleton makes no model calls.

From the project root:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
.\mvnw.cmd --batch-mode verify
& "$env:JAVA_HOME\bin\java.exe" -jar target/sentinel-0.1.0-SNAPSHOT.jar --check-config
```

Adjust JAVA_HOME to your JDK folder if different. Maven resolves pinned plugins, Jackson Core and JUnit, compiles code, runs the MVP tests and packages a JAR containing the runtime JSON dependency. Test reports appear under `target/surefire-reports/`. On macOS/Linux, use `sh mvnw --batch-mode verify`.

## Local configuration

The local `.env` is deliberately completely empty and ignored by Git. `.env.example` contains blank optional settings and no credentials. For a fresh clone, copy it only if `.env` does not already exist:

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
```

Configuration precedence is bundled `application.properties` < local `.env` < process environment. Blank overrides retain lower-priority values. Defaults use `http://localhost:11434`, model `qwen2.5:7b`, and storage under `runtime/`. Relative paths resolve against the working directory; configuration loading does not create directories or contact Ollama.

Supported variables: `SENTINEL_OLLAMA_URL`, `SENTINEL_OLLAMA_MODEL`, `SENTINEL_DATA_DIR`, `SENTINEL_CASE_DIR`, and `SENTINEL_EXPORT_DIR`. The simple UTF-8 .env format accepts blank lines, full-line comments, KEY=value, and matching single/double quotes. It performs no variable expansion, escape processing or inline-comment parsing; unknown variable names are ignored. Do not use shell `export` prefixes. Invalid entries produce a line-number error without echoing their contents.

Stage 1 budgets remain 8 tool attempts, 2 plan revisions, 6 model calls, 10/60-second tool/model timeouts and one transient retry. ToolExecutor sessions now enforce the tool-attempt, timeout and transient-retry ceilings. AgentEngine orchestration, plan-revision and model-call enforcement remain pending.

Local Ollama needs no API key. If a future provider requires one, enter it only in ignored local configuration. Never place secrets in source, snapshots, logs, videos or reports.

## GUI and CLI launch contracts

Startup diagnostic commands:

```powershell
java -jar target/sentinel-0.1.0-SNAPSHOT.jar --help
java -jar target/sentinel-0.1.0-SNAPSHOT.jar --check-config
```

They return 0 on success; invalid configuration/unsupported arguments return 2. The future `--gui` and `--cli` modes currently return 2 rather than claiming successful startup.

Planned CLI operations from Stage 1:

```text
sentinel new --request "Investigate the demo account" --dataset D
sentinel import --case C --file data/samples/account-takeover.json
sentinel query --case C --tool logSearch --args JSON
sentinel run --case C
sentinel ask --case C --question "Which evidence supports the assessment?"
sentinel save --case C
sentinel open --case C
sentinel export --case C --out runtime/exports/report.md
```

`sentinel` is a planned command name, not an installed launcher. Scope input must eventually include a target and UTC interval. Planned commands emit JSON, progress on stderr, and exit codes 0/2/3 for success/invalid input/operational failure.

## Data and storage

`data/samples/import-*.json` contains synthetic fixtures for the implemented import layer. Other scenario/feed/inventory files remain placeholders. Dataset versions are currently held in memory, with immutable earlier views. Runtime disk persistence is not implemented; `runtime/cases`, `runtime/datasets`, `runtime/exports` and `runtime/logs` reserve the local storage locations. JSON persistence needs no SQL service.

After packaging, run the import-layer walkthrough:

```powershell
& "$env:JAVA_HOME\bin\jshell.exe" --class-path target/sentinel-0.1.0-SNAPSHOT.jar demos/import-demo.jsh
```

This reads the synthetic files without changing them or writing case data. Expected results: the first import accepts two events and counts one duplicate; reimport counts three duplicates; conflicting and invalid batches are rejected, leaving version `v1` with two events. Run `.\mvnw.cmd verify` for the automated checks.

### Import format

Input is a UTF-8 JSON array (up to 10 MiB and 100,000 entries). Each event requires nonblank string fields `eventId`, `timestamp` (ISO-8601 with `Z` or a UTC offset), and `eventType`. Optional string fields are `accountId`, `sourceIp`, `hostId`, and `outcome`; missing, null or blank optional values mean unknown. See `data/samples/import-valid.json` for an example.

Timestamps normalize to UTC. Unknown fields, duplicate JSON keys, invalid types and malformed records reject the batch. Identical event IDs/content count as duplicates; conflicting content under one ID rejects the entire batch. Source provenance is generated internally. Versions advance only when new events are accepted, and previous versions remain immutable in memory.

### Filtered log search (component demo)

After packaging, run `& "$env:JAVA_HOME\bin\jshell.exe" --class-path target/sentinel-0.1.0-SNAPSHOT.jar demos/search-demo.jsh` in PowerShell. Expected: SUCCESS with two events, PARTIAL with one capped event, EMPTY for an unknown account, and INVALID_ARGUMENT for limit zero. The dataset remains v1 with two events.

Search requires `datasetId`, `datasetVersion`, `start`, `end`, and `limit` (1-1000). Times require a UTC offset and both endpoints are inclusive. Optional exact-match filters are `eventId`, `eventType`, `accountId`, `sourceIp`, `hostId`, and `outcome`; filters combine with AND. Results sort by timestamp then event ID. Narrow the interval or filters when a result is PARTIAL. Evidence preserves source location and event content, with a stable ID per dataset/read-version/event. The original introduction version remains in the payload. ToolExecutor sessions enforce supplied case scope; automatic active-case wiring and GUI/CLI integration remain pending.

### Account activity (component demo)

After packaging, run `& "$env:JAVA_HOME\bin\jshell.exe" --class-path target/sentinel-0.1.0-SNAPSHOT.jar demos/activity-demo.jsh`. Expected: SUCCESS with ordered IDs demo-1/demo-2, one success, one failure and one distinct host; PARTIAL with one returned event and two total matches; EMPTY with explicit zero counts/interval for an unknown account; INVALID_ARGUMENT for limit zero. The dataset remains v1 with two events.

The `userActivity` arguments are `datasetId`, `datasetVersion`, `accountId`, `start`, `end`, and `limit` (1-1000), all required. Account matching is exact with no alias expansion; times require offsets and include both endpoints. Events across all event types sort by timestamp then event ID and use the same source-linked evidence IDs as log search.

`ToolResult.activitySummary()` supplies typed data for activity results (null for other tools). Counts cover **all matches** in the pinned version and requested interval, even when evidence is capped: total/returned events, exact lowercase success/failure outcomes, unknown outcomes, distinct known hosts and events with unknown hosts. Unrecognized outcome values remain unknown. Coverage includes the requested interval and first/last matching timestamps (null for empty results). These describe available stored events; they do not guarantee complete source logging or imply safety. ToolExecutor sessions enforce supplied scope; GUI/CLI and automatic case-evidence attachment remain pending.

### Local indicator lookup (component demo)

After packaging, run `& "$env:JAVA_HOME\bin\jshell.exe" --class-path target/sentinel-0.1.0-SNAPSHOT.jar demos/indicator-demo.jsh`. It uses a fixed October 27 review clock: expect SUCCESS/MALICIOUS for 192.0.2.10, STALE/SUSPICIOUS for OLD.EXAMPLE., UNKNOWN with no evidence for missing.example, INVALID_ARGUMENT for a URL, and FEED_UNAVAILABLE with no loaded feed. Event data remains v0/empty.

Load a local UTF-8 JSON feed with `DatasetStore.loadIndicatorFeed(path)`, then call `IndicatorLookupCommand` with tool name `indicatorLookup` and exactly two required arguments: `indicator` and `feedVersion`. Loading is explicit; startup does not automatically load a feed. The sample is `data/indicators/threat-feed.json`:

```json
{
  "feedVersion": "demo-feed-v1",
  "records": [
    {
      "indicator": "192.0.2.10",
      "verdict": "MALICIOUS",
      "source": "Synthetic teaching feed",
      "observedAt": "2026-10-26T12:00:00Z"
    }
  ]
}
```

All shown fields are required nonblank strings; records may be empty. Verdicts are exactly MALICIOUS, SUSPICIOUS, BENIGN or UNKNOWN. Observation dates require a UTC offset, normalize to UTC and cannot be in the future at load time. Limits are 10 MiB/100,000 records. Unknown/duplicate JSON fields, duplicate normalized indicators, invalid types/encoding/dates and trailing content reject the whole load. Failed loads preserve earlier loaded versions. Identical content under an existing version is idempotent; different content under that version is rejected. Versions remain in memory only.

Indicators accept IPv4, IPv6 literals and ASCII domains with at least two labels. Surrounding whitespace is stripped; domains lowercase and lose one trailing dot; IPv6 spellings normalize to the same numeric form. URLs, ports, wildcards, IPv6 zone IDs, ambiguous IPv4 leading zeros and Unicode domains are rejected. Lookup makes no network requests.

`ToolResult.indicatorMatch()` supplies normalized indicator, match flag, verdict, source, observation date, feed version and stale flag (null for other tools). No match returns status UNKNOWN, no evidence and no invented source/date; UNKNOWN is never treated as safe. Matched records return SUCCESS, or STALE when observation age is **greater than 30 days** by default. A stale record retains its original verdict and date. A matched record whose feed verdict is UNKNOWN still has a true match flag and source evidence. The clock and positive age threshold can be injected through the DatasetStore constructor; no new environment overrides are introduced.

Matched evidence has a stable ID per indicator/feed version, a JSON payload and file/record provenance. Result sourceVersion and evidence datasetVersion identify the **feed version** for this tool. A missing/unreadable file or lookup with no loaded feeds raises FEED_UNAVAILABLE; an unloaded version when other feeds exist raises UNKNOWN_FEED_VERSION; invalid feed content raises INVALID_FEED. ToolExecutor sessions validate supplied target/feed scope; GUI/CLI and automatic case-evidence attachment remain pending.

### Controlled tool execution (component demo)

After packaging, run `& "$env:JAVA_HOME\bin\jshell.exe" --class-path target/sentinel-0.1.0-SNAPSHOT.jar demos/executor-demo.jsh`. Expected: scoped SUCCESS with two events, DUPLICATE_CALL for equivalent arguments, OUT_OF_SCOPE for a different account, PARTIAL account activity with one returned/two total, and SUCCESS/MALICIOUS for an observed indicator. Three accepted attempts leave five remaining. A separate session refuses its ninth attempt with BUDGET_EXHAUSTED; event data stays v1/two events.

`ToolExecutor.local(store)` registers only logSearch, userActivity and indicatorLookup. An explicit registry constructor can select a subset of these names and accepts `AppConfig.ExecutionLimits`; the default is eight attempts, ten seconds per attempt and one retry per explicitly transient failure. Injected limits may tighten these ceilings. Close the executor with try-with-resources.

Execution requires `openSession(ToolScope)`. Direct `ToolExecutor.execute(call)` refuses unscoped execution. The trusted application supplies an immutable CaseScope, explicit ACCOUNT/INDICATOR target kind, selected feed version and normalized indicators from accepted in-scope evidence; model proposals cannot authorize scope. A target kind is explicit so domain-looking account names are not guessed to be indicators.

| Tool | Session scope requirement |
| --- | --- |
| logSearch | Exact case dataset/version, interval within the inclusive case bounds, and target accountId; indicator targets instead require a matching sourceIp |
| userActivity | Account scope, exact target accountId and case dataset/version, with interval inside case bounds |
| indicatorLookup | Selected feedVersion plus the indicator target or an indicator from accepted case evidence |

Domain event search is refused because the current event schema has no domain field. Schema/registry validation through `ToolExecutor.validate`, or schema plus scope validation through `Session.validate`, performs no retrieval and spends no attempts. Invalid/unregistered/out-of-scope calls never reach a command.

A session owns its attempt counter and duplicate history for an explicit run/query. `ToolCall.key()` ignores the UUID and argument order, normalizes UTC offsets and numeric limits, and normalizes indicator-lookup spelling; exact-match search filter text remains exact. Equivalent submitted calls are refused even after failure or timeout. The trusted application may call `Session.acceptEvidenceIndicators` after accepting new in-scope evidence; this preserves the same budgets and duplicate history. Case attachment and this automatic authorization flow will be wired in a later increment. New sessions are explicit new operation budgets, not a way to resume a run with reset counters.

Only TRANSIENT_FAILURE permits one internal retry, charged as another attempt. Timeout, interruption, permanent failures and duplicate calls are not retried. Timeout includes queue wait; expired/cancelled results are discarded. A single daemon worker and one queue slot bound resources even if a command ignores interruption. Such a command can keep the worker occupied until it returns; queued calls can time out and excess submissions return EXECUTOR_BUSY. Rejected submissions spend no attempt; accepted submissions, including cancelled ones, do. Closing cancels outstanding results. Unexpected command errors use a fixed diagnostic rather than exposing exception contents.

This increment implements the Command collaboration at component level. AgentEngine/controller wiring, evidence attachment, audit/staleness updates, model budgets and GUI/CLI workflows remain pending.

## Verification

The latest recorded build passed 212 tests: configuration/startup (17), domain contracts (23), lifecycle/snapshots (25), import (31), log search (20), account activity (24), indicator lookup (41), and controlled execution (31). Run `.\mvnw.cmd verify`; results are written to `target/surefire-reports/`. Tests run without Ollama. Full deterministic testing and KUMA validation remain Stage 3 work.

## Submission records

See `docs/feature-implementation-mapping.md`, `docs/design-patterns.md`, `docs/ai-tools-and-costs.md`, `docs/ai-collaboration-log.md`, `docs/verification-log.md`, `docs/weekly-commit-summary.md` and `docs/demonstration-index.md`.

The Stage 2 report and final GUI/CLI demonstration recordings are pending. Submission records are grouped under `docs/`. Private planning, review, reference and historical notes are grouped under `local-notes/`, which is excluded from version control. Source code, runtime resources, sample data and executable demos remain in their standard project directories.

## Active-case import and evidence integration (Step 9)

AgentController owns one active Investigation and shares its DatasetStore with LogImporter and AgentEngine. ResultVerifier checks canonical source payloads, provenance and query scope before Investigation atomically accepts evidence; conflicting IDs reject the result. Overlapping search/activity results deduplicate without staling an assessment unless new evidence is attached. Canonical accepted event indicators authorize enrichment within the existing executor session, preserving budgets and duplicate history.

New successful import versions update the active case scope, retain historical evidence, invalidate its plan/sessions and stale its assessment. Rejected and duplicate-only imports preserve version, plan and analysis. Target/feed metadata survives snapshots. EventBus delivers immutable progress and isolates listener failures. Planning, analysis, GUI/CLI workflows and disk persistence remain later work; start creates a draft without planning.

Verify with `./mvnw.cmd --batch-mode verify` (245 tests), then run the packaged component walkthrough:

```powershell
& "$env:JAVA_HOME\bin\jshell.exe" --class-path target/sentinel-0.1.0-SNAPSHOT.jar demos/case-integration-demo.jsh
```

The walkthrough uses a fixture plan solely to demonstrate import invalidation; it does not implement rule or model planning. All sample data is synthetic.
