# Sentinel CLI user guide and command reference

Applies to the current packaged Java CLI. The [GUI guide](gui-user-guide.md) explains the equivalent buttons, result tabs, lifecycle and evidence display. Both interfaces use the same application services and saved cases. See [model setup](model-setup.md) for installation and [implementation architecture](implementation-architecture.md) for service design.

## Install, build and start

Run from the project root in PowerShell 7.3+ on Windows:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
.\scripts\setup-qwen.ps1
```

Adjust JAVA_HOME to the installed full JDK 17+ path. Setup also requires Python 3.10+. It starts/configures local Ollama/Qwen, builds/tests Sentinel and verifies real model integration. Omit `--launch` for CLI-only setup. On Linux use `bash scripts/setup-qwen.sh`; see model setup for native prerequisites.

To build without model setup:

```powershell
.\mvnw.cmd --batch-mode verify
& "$env:JAVA_HOME\bin\java.exe" -jar '.\target\sentinel-0.1.0-SNAPSHOT.jar' --check-config
```

`--check-config` checks configuration only; it does not contact Qwen. Direct CLI calls do not automatically start the model server. After reboot, rerun setup before using model-dependent commands.

There is no interactive Sentinel command shell or installed `sentinel` executable. Each invocation starts a JVM, performs one action, prints a result and exits. The following optional PowerShell wrapper shortens the commands without changing their output:

```powershell
function sentinel {
    & "$env:JAVA_HOME\bin\java.exe" -jar '.\target\sentinel-0.1.0-SNAPSHOT.jar' @args
}
```

Examples below use this wrapper. On Linux/macOS replace `sentinel` with `java -jar target/sentinel-0.1.0-SNAPSHOT.jar`. Shell variables and JSON quoting must follow the chosen shell's syntax; the walkthrough below specifically targets PowerShell 7.3+ with standard native argument passing.

## Syntax and output contract

```text
java -jar target/sentinel-0.1.0-SNAPSHOT.jar COMMAND --name value ...
```

Options are separate arguments. `--rule` and `--overwrite` are switches without a following value. Duplicate options are rejected. Use documented options; the current parser does not reliably reject all unknown names. `--help` is a top-level diagnostic, not a per-command help switch.

| Stream / code | Meaning |
| --- | --- |
| stdout | A JSON Result envelope on a successful dispatch: `{"status":"SUCCESS","message":"..."}`. `message` usually contains another JSON document encoded as a string. |
| stderr | Progress such as `CASE: OPENED`, `RETRIEVAL: PARTIAL`, `PLANNING: MODEL:READY`, plus failure diagnostics. |
| Exit 0 | Dispatch returned SUCCESS. Inspect payload and progress: PARTIAL, EMPTY, UNKNOWN, or a case with blocked planning can still return 0. |
| Exit 2 | Invalid input/scope, or a returned REJECTED result such as an invalid import batch. |
| Exit 3 | Operational failure, including tool/provider/storage failures outside the input-error category. |

Read the **domain result**, not just the exit code. An incomplete run can return a successful envelope containing a PARTIAL assessment. A new case can be saved as DRAFT when planning is blocked.

To decode an ordinary JSON payload in PowerShell:

```powershell
$Raw = sentinel show --case $CaseId
$Code = $LASTEXITCODE
if ($Code -ne 0) { throw "Sentinel exited $Code" }
$Envelope = ($Raw -join "`n") | ConvertFrom-Json
$Case = $Envelope.message | ConvertFrom-Json
$Case | ConvertTo-Json -Depth 30
```

Keep stderr separate when parsing stdout; do not merge `2>&1` into JSON input. `preview` is an exception: its message is plain Markdown, so print `$Envelope.message` directly. PowerShell may deserialize ISO timestamps into date objects; compare instants rather than assuming their decoded type is always string.

## Command reference

Use `--case UUID` for operations on an existing case. Separate processes do not retain an active case. The saved snapshot determines its dataset; normally no separate `--dataset` is needed with `--case`.

| Command | Options / prerequisite | Result and GUI equivalent |
| --- | --- | --- |
| `new` | `--request TEXT`; supply all of `--target VALUE --start INSTANT --end INSTANT` for complete scope. Optional `--dataset ID` (default local), `--version VERSION` (default v0), `--type ACCOUNT\|INDICATOR` (default ACCOUNT), `--feed VERSION`, `--rule`. | New snapshot/UUID, usually READY after accepted planning or DRAFT if blocked. Without complete scope, clarification is required. GUI: New case. |
| `import` | `--case UUID --file PATH`; scoped case, neither RUNNING nor FAILED. | Summary with accepted/duplicate counts and dataset version. Changed versions invalidate plan/analysis. GUI: Import JSON. |
| `query` | `--case UUID --tool NAME --args JSON`; valid in-scope string-valued JSON arguments. | ToolResult: status, evidence, sourceVersion, durationMillis, truncated, activitySummary, indicatorMatch. GUI: Query. |
| `plan` | `--case UUID`; complete valid scope and available model budget/provider. | Accepted MODEL plan, or null with a PLANNING failure outcome. Planning does not run collection tools. GUI: Generate model plan. |
| `rule` | `--case UUID`; explicit analyst approval of basic planning. | Accepted RULE plan on the same case. GUI: Approve rule plan. |
| `clarify` | `--case UUID --dataset ID --version VERSION --target VALUE --start INSTANT --end INSTANT`; DRAFT only. | Applies scope and requests model planning. GUI: Clarify scope. |
| `revise` | `--case UUID --target VALUE --start INSTANT --end INSTANT`; COMPLETED only. | Retains dataset/version and UUID, clears plan, sets DRAFT and marks assessment STALE. GUI: Revise completed scope. |
| `run` | `--case UUID`; READY plan. Optional `--criticalHosts host1,host2`. | Assessment; inspect completeness and then `show` for final case status/history. GUI: Run. |
| `ask` | `--case UUID --question TEXT`; scoped case, neither RUNNING nor FAILED. | Cited answer; failures are recorded in Conversation. Prefix `refresh:` for explicit scoped collection before answering. GUI: Ask. |
| `resume` | `--case UUID`; PAUSED case. | Revalidated plan/READY, without execution. Follow with run. GUI: Revalidate paused plan. |
| `save` | `--case UUID`. | Saved revision number. Successful mutations already save automatically. GUI: Save. |
| `open` | `--case UUID`. | Restored snapshot. RUNNING restores as PAUSED. The process then exits; later commands still require --case. GUI: Open case. |
| `show` | `--case UUID`. | Current snapshot including accumulated evidence, plan, assessment, conversation and history. GUI equivalent: inspect tabs. |
| `list` | Optional `--dataset ID` to select the process dataset. | Saved case summaries from the configured case repository; this is not a dataset-filtered list. GUI: Open case chooser. |
| `preview` | `--case UUID`. | Markdown report in the envelope's message. GUI: Report tab. |
| `export` | `--case UUID --out PATH`; optional `--overwrite`. | Export path. Existing destination requires explicit --overwrite; no interactive CLI confirmation. GUI: Export Markdown. |
| `inventory` | `--criticalHosts host1,host2`; normally also --case for the active case/save context. | Process-local inventory acknowledgement. Because the process immediately exits, use run --criticalHosts for useful CLI analysis. GUI: Critical hosts field on Run. |
| `gui` | Optional `--dataset ID`. | Opens Swing instead of executing a terminal workflow. See the GUI guide. |
| `--help` | Used alone. | Usage and command summary, exit 0. |
| `--check-config` | Used alone. | Configuration diagnostics, exit 0 if valid; no model connectivity check. |

For scope/status rules, see [GUI case status and completeness](gui-user-guide.md#case-status-and-completeness). The CLI has no standalone stop command. Terminating a process is not equivalent to the GUI's cooperative Stop; a last saved RUNNING snapshot restores as PAUSED.

## Worked example on Windows

These commands use supplied public synthetic data. They create real saved cases and datasets. Use an unused dataset ID for a clean v0 → v1 example; otherwise choose a new ID and change it consistently in the query JSON.

First define the `sentinel` wrapper above, then a convenience decoder:

```powershell
$PSNativeCommandArgumentPassing = 'Standard'
function Read-SentinelResult {
    param([string[]]$Output)
    $Envelope = ($Output -join "`n") | ConvertFrom-Json
    if ($Envelope.status -ne 'SUCCESS') { throw "Result: $($Envelope.status)" }
    $Envelope.message | ConvertFrom-Json
}

$Raw = sentinel new --dataset analyst_cli_demo --request 'Investigate a possible account takeover for demo-analyst within the supplied interval.' --target demo-analyst --start '2026-10-22T12:00:00Z' --end '2026-10-22T13:00:00Z' --feed demo-feed-v1 --rule
if ($LASTEXITCODE -ne 0) { throw 'Case creation failed' }
$Case = Read-SentinelResult -Output $Raw
$CaseId = $Case.id
$CaseId

sentinel import --case $CaseId --file '.\data\scenarios\account-takeover.json'
```

`--rule` intentionally chooses deterministic planning for this walkthrough; omit it to demonstrate Qwen planning. Creation alone collects no evidence. First import accepts six events into v1 and returns the case to DRAFT because its v0 plan is invalidated. Reimporting the same file counts duplicates without advancing the version. Inspect `IMPORT_COMPLETED` in show/history for counts.

The [shared import format](gui-user-guide.md#import-file-format) defines required fields, timestamp rules, size limits and atomic rejection behaviour for both interfaces.

### Log search and result limits

```powershell
$Raw = sentinel query --case $CaseId --tool logSearch --args '{"datasetId":"analyst_cli_demo","datasetVersion":"v1","start":"2026-10-22T12:00:00Z","end":"2026-10-22T13:00:00Z","accountId":"demo-analyst","limit":"6"}'
if ($LASTEXITCODE -ne 0) { throw 'Search failed' }
$Search = Read-SentinelResult -Output $Raw
$Search | ConvertTo-Json -Depth 20

sentinel query --case $CaseId --tool logSearch --args '{"datasetId":"analyst_cli_demo","datasetVersion":"v1","start":"2026-10-22T12:00:00Z","end":"2026-10-22T13:00:00Z","accountId":"demo-analyst","limit":"2"}'

sentinel query --case $CaseId --tool logSearch --args '{"datasetId":"analyst_cli_demo","datasetVersion":"v1","start":"2026-10-22T12:00:00Z","end":"2026-10-22T13:00:00Z","accountId":"demo-analyst","limit":"100","outcome":"blocked"}'
```

Expected: six chronologically ordered events with SUCCESS/truncated false; two events with PARTIAL/truncated true; then EMPTY with no matching evidence. Each query prints its own result. `show` and exported reports still retain all accepted evidence. See the [GUI Evidence display contract](gui-user-guide.md#evidence-display-contract) for the matching screen behaviour.

`logSearch` requires datasetId, datasetVersion, start, end and limit (1–1000). Optional exact-match filters are eventId, eventType, accountId, sourceIp, hostId and outcome; they combine with AND. Every JSON value must be a string, so `"limit":"2"` is valid and `"limit":2` is rejected. Both interval endpoints are inclusive and must stay inside approved case scope.

### Account activity and coverage

```powershell
$Raw = sentinel query --case $CaseId --tool userActivity --args '{"datasetId":"analyst_cli_demo","datasetVersion":"v1","accountId":"demo-analyst","start":"2026-10-22T12:00:00Z","end":"2026-10-22T13:00:00Z","limit":"100"}'
if ($LASTEXITCODE -ne 0) { throw 'Activity query failed' }
$Activity = Read-SentinelResult -Output $Raw
$Activity.activitySummary | ConvertTo-Json -Depth 10
```

All shown arguments are required. For this fixture expect totalEvents 6, returnedEvents 6, successes 1, failures 5, unknownOutcomes 0, distinctHosts 1 and unknownHostEvents 0. First event is 12:00 UTC and last event 12:06 UTC on October 22, 2026. With limit `"2"`, expect totalEvents 6 but returnedEvents 2 and PARTIAL. Summary counts cover all matching stored events, not just the returned evidence. The GUI currently does not display this typed summary in a separate panel.

### Enrich an observed indicator

```powershell
$Raw = sentinel query --case $CaseId --tool indicatorLookup --args '{"indicator":"192.0.2.10","feedVersion":"demo-feed-v1"}'
if ($LASTEXITCODE -ne 0) { throw 'Indicator lookup failed' }
$Lookup = Read-SentinelResult -Output $Raw
$Lookup | ConvertTo-Json -Depth 20
```

Both keys are required. The preceding search/activity evidence authorizes lookup of the observed IP within the ACCOUNT case. The supplied local feed contains a MALICIOUS verdict dated October 26, 2026. Inspect indicatorMatch and evidence; stale is date-dependent and becomes true when age exceeds 30 days. UNKNOWN means no feed match, not a safe verdict. To query an unrelated indicator, create an explicitly scoped INDICATOR case instead of broadening this account case.

### Plan, run, ask and export

```powershell
sentinel rule --case $CaseId
# Alternatively request Qwen planning: sentinel plan --case $CaseId

sentinel run --case $CaseId
sentinel show --case $CaseId
sentinel ask --case $CaseId --question 'What evidence supports this assessment?'
sentinel save --case $CaseId
sentinel export --case $CaseId --out '.\runtime\exports\analyst-cli-demo.md'
```

Inspect returned assessment completeness, limitations and case status rather than assuming every run is complete. Hypotheses and recommendations need a working model even with a RULE collection plan. To explicitly mark `workstation-1` critical, pass `--criticalHosts workstation-1` on run; that changes a severity-policy input. Resupply inventory on each relevant CLI run because process memory does not survive exit.

Use `sentinel ask --case $CaseId --question 'refresh: What evidence supports this assessment?'` for explicit scoped refresh. Use `sentinel preview --case $CaseId` for a report without writing a file. Repeating export to an existing path requires adding `--overwrite`; the report includes accumulated evidence, status, model/prompt/policy identifiers, analysis, conversation and history. Export performs no new inference.

## Scope changes and recovery

For a DRAFT case, explicitly clarify scope:

```powershell
sentinel clarify --case $CaseId --dataset analyst_cli_demo --version v1 --target demo-analyst --start '2026-10-22T12:00:00Z' --end '2026-10-22T13:00:00Z'
```

For a COMPLETED case, revise first, then plan and run:

```powershell
sentinel revise --case $CaseId --target demo-analyst --start '2026-10-22T12:00:00Z' --end '2026-10-22T12:30:00Z'
sentinel plan --case $CaseId
sentinel run --case $CaseId
```

For a PAUSED case, use `resume --case UUID`, inspect READY, then `run --case UUID`. FAILED is terminal for retrieval/import/replanning; start a new case. A new dataset version invalidates the old plan and makes existing analysis stale. Plan acceptance does not run tools.

## Storage, interoperability and troubleshooting

Default storage is `runtime/datasets`, `runtime/cases` and `runtime/exports`, relative to the working directory. `.env` or process variables can select other directories; see model setup. To share a case between GUI and CLI, use the same project/configuration/storage, launch the GUI with the case's dataset, and open its UUID. Use one writer per dataset and avoid concurrent changes to the same case. Saving a case is not a self-contained backup of its source dataset.

| Symptom | Action |
| --- | --- |
| JSON cannot be parsed | Parse stdout only, then decode the envelope's message. Check the process exit code before decoding. |
| INVALID_ARGUMENT | Check string-valued JSON, required keys, limits 1–1000 and chronological times. |
| OUT_OF_SCOPE | Retain the case account/indicator, pinned dataset/version and approved interval, or explicitly revise scope. |
| MODEL_UNAVAILABLE | Rerun setup to start/warm Qwen; verify configured endpoint/model. `--check-config` alone cannot test connectivity. |
| INVALID_PLAN / DRAFT after new | Strict model validation blocked planning. Read stderr/history; retry or explicitly approve rule planning. |
| Run rejected after import | Accept a fresh plan for the changed version. |
| PARTIAL despite exit 0 | Read completeness, truncation, tool/model failures and limitations. Exit 0 is not a completeness guarantee. |
| Cannot open/query old case | Check case storage and source dataset files. Use show to inspect retained data and history. |
| Export destination already exists | Choose another name or explicitly add --overwrite. |
| Expected invalid-input demonstration | Invoke the native command and inspect exit 2/stderr; convenience wrappers that throw on nonzero may add their own PowerShell exception. |

For the graphical equivalent and detailed control behaviour, return to the [GUI guide](gui-user-guide.md). The [reviewer verification guide](reviewer-verification.md) covers broader project verification.
