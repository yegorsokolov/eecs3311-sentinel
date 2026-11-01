# Sentinel GUI user guide and behaviour reference

Applies to the current Java 17 Swing implementation. This guide describes the implemented interface, including its limits; it is separate from the architecture and submission report.

For the same operations in a terminal, see the [CLI user guide](cli-user-guide.md). For installation, see [model setup](model-setup.md); for internal service design, see [implementation architecture](implementation-architecture.md).

## Start Sentinel on Windows

Open PowerShell in the project root. Install Python 3.10+ and a full JDK 17+ first, then run:

```powershell
.\scripts\setup-qwen.ps1 --launch
```

This prepares local Ollama/Qwen, builds and tests Sentinel, verifies a real model connection, and opens the GUI. Rerun setup after a reboot if the local model server is stopped. Downloading the model and cold loading it can take several minutes.

For an already built installation with Qwen running:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
& "$env:JAVA_HOME\bin\java.exe" -jar '.\target\sentinel-0.1.0-SNAPSHOT.jar' gui --dataset analyst_demo
```

Adjust the JDK path to your installation. Run from the project root so local configuration, the sample feed, and relative paths resolve correctly. The dataset name accepts 1–64 letters, digits, underscores or hyphens. It defaults to `local` when omitted. Select a different dataset by closing the window and launching with another `--dataset`; there is no dataset selector inside the window.

## Window layout

The top form defines the investigation request and scope. Case controls sit below it. The tool dropdown, JSON argument field and Query controls perform individual retrievals. The central tabs show read-only results. The bottom question field and **Ask** button handle follow-ups; the progress area underneath reports outcomes.

After a case is created or opened, the window title includes its UUID and status. Keep that UUID when referring to the same case from the CLI. The title reflects the last rendered snapshot; use progress messages to follow an operation currently running.

Most action buttons are disabled during a background operation. **Stop after current operation** remains available. Fields may still be editable, but an operation uses the inputs captured when its button was clicked. Results refresh after completion and text views return to the top.

## Form fields

| Field | What to enter | When it takes effect |
| --- | --- | --- |
| Request | A nonblank investigation goal in natural language. Example: `Investigate a possible account takeover for demo-analyst within the supplied interval.` | **New case** captures the goal. Editing this field does not rename or revise an existing case. |
| Target | An exact account ID for ACCOUNT, or an IP/domain indicator for INDICATOR. | New case, Clarify scope, or Revise completed scope. |
| Target type | ACCOUNT or INDICATOR. | New case. Changing the dropdown does not convert an existing case. |
| Start UTC | ISO-8601 instant, for example `2026-10-22T12:00:00Z`. | New case or an explicit scope action. |
| End UTC | ISO-8601 instant, for example `2026-10-22T13:00:00Z`; cannot precede Start UTC. | New case or an explicit scope action. Both query interval endpoints are inclusive. |
| Feed version | A loaded feed version, such as `demo-feed-v1`. | New case captures the selection. Editing it alone does not replace the active case's feed. |
| Use approved basic rule plan | Check to explicitly choose deterministic basic planning for a new case. | New case only. Unchecked requests a model plan. |
| Dataset | Read-only label showing the dataset selected at launch. | Launch time. Case queries must use a compatible dataset and version. |
| Critical hosts (comma separated) | Exact host IDs, such as `workstation-1,server-2`; no spaces around IDs. Leave blank when criticality is unknown. | Run. Inventory is process-local; it is not a saved inventory editor. |

Successful actions render the stored scope back into the form. Typing into fields alone does not change saved scope. For a new investigation with another goal, target type or feed, use **New case**. The application does not have general in-place editing for these properties.

## Case and analysis buttons

| Button | Behaviour and prerequisite | What to expect |
| --- | --- | --- |
| New case | Creates a new UUID from the form, using the current dataset version known to the window, or v0 before a case has been loaded. Requests model planning unless basic rules are checked. | A validated plan gives READY. Planning failure leaves an inspectable DRAFT. The previous case is not deleted. No collection tool runs during planning. |
| Import JSON | Opens a file chooser and imports into the active case's dataset. Requires a scoped case that is neither RUNNING nor FAILED. | New events publish a new version, clear the plan and return the case to DRAFT. Existing analysis becomes STALE. Duplicate-only imports retain the version; rejected batches commit no events. |
| Generate model plan | Requests and validates a Qwen plan for the active case. | Inspect origin MODEL in Plan and `PLANNING: MODEL:READY` in progress. Does not execute the plan. |
| Approve rule plan | Explicitly accepts a validated deterministic basic plan on the same case. | Plan origin RULE and READY. It is an analyst choice; model failure does not silently choose it. |
| Clarify scope | Applies Target, Start UTC and End UTC, with the window's dataset/version, to a DRAFT case, then requests planning. | Corrected scope is saved; planning can still be blocked. |
| Revise completed scope | Applies the changed target/time interval to a COMPLETED case. | Same UUID returns to DRAFT, plan is cleared and assessment becomes STALE. Generate or approve a new plan before Run. |
| Run | Requires READY with a valid plan. Collects scoped evidence and performs analysis; uses Critical hosts when supplied. | Progress, accepted evidence, timeline, fixed severity, model hypotheses and recommendations. Result may be COMPLETED or explicitly PARTIAL. |
| Stop after current operation | Requests cooperative cancellation of an active Run. | Does not instantly terminate a tool/model request. The run checks cancellation at operation boundaries and retains accepted evidence with a partial result. It is not a general cancel button for every action. |
| Revalidate paused plan | Requires a PAUSED case restored from a saved RUNNING snapshot. | Validates the saved plan and returns to READY. It does not resume execution automatically; click Run afterward. |
| Save | Saves the active case snapshot explicitly. | Successful case mutations already save automatically. Save is useful as an explicit checkpoint, not as a commit button for unsubmitted form edits. |
| Open case | Lists saved cases by UUID, status and goal; choose one and confirm. | Restores the case, tabs and scope. Launch the GUI with that case's dataset to perform further retrievals. Opening does not change the dataset selected for the process. |
| Export Markdown | Opens a save dialog for the active case report. Enter a filename ending in `.md`. | Writes a report from accepted saved values without a new model request. Existing files require **Replace existing export?** confirmation. Cancel leaves the export untouched. |

Only one case is active per window. Use one writer per dataset and avoid editing the same case concurrently from another GUI or CLI. Case and dataset files are saved separately; there is no shared multi-user editing session.

## Import file format

Import JSON accepts a UTF-8 JSON array, up to 10 MiB and 100,000 events. Each event requires nonblank string fields eventId, timestamp and eventType. Optional string fields accountId, sourceIp, hostId and outcome may be omitted or unknown. Timestamps need `Z` or an explicit UTC offset and normalize to UTC.

```json
[{"eventId":"example-1","timestamp":"2026-10-22T12:00:00Z","eventType":"login","accountId":"demo-analyst","sourceIp":"192.0.2.10","hostId":"workstation-1","outcome":"failure"}]
```

Unknown fields, duplicate JSON keys, invalid field types and conflicting content under an existing event ID reject the entire batch. Identical records count as duplicates. Only accepted new events advance the version; previous versions remain retained. Sentinel creates source provenance internally. For a supplied complete file, use `data/scenarios/account-takeover.json`.

## Manual queries

Choose **logSearch**, **userActivity**, or **indicatorLookup**, enter a JSON object in the argument field, then click **Query**. Every argument value must be a JSON string, including `limit`. Keys and exact-match filter values are case-sensitive where defined by the underlying data.

**Fill scope arguments** replaces the entire JSON field with an editable template from the active case. For event tools it uses limit `"1000"`. For indicator lookup in an ACCOUNT case it suggests the sample IP `192.0.2.10`; verify it against accepted evidence before querying. It does not automatically discover an arbitrary account's indicator. An INDICATOR scope is filled using `sourceIp` for event retrieval; `userActivity` still requires an account ID, so use that tool with an ACCOUNT case.

### logSearch

```json
{"datasetId":"analyst_demo","datasetVersion":"v1","start":"2026-10-22T12:00:00Z","end":"2026-10-22T13:00:00Z","accountId":"demo-analyst","limit":"6"}
```

Required keys: datasetId, datasetVersion, start, end and limit (1–1000). Optional exact-match filters: eventId, eventType, accountId, sourceIp, hostId and outcome. Filters combine with AND. Results sort by timestamp, then event ID. A query must stay inside the active case's account/indicator and interval; typing broader JSON does not broaden approved scope.

### userActivity

```json
{"datasetId":"analyst_demo","datasetVersion":"v1","accountId":"demo-analyst","start":"2026-10-22T12:00:00Z","end":"2026-10-22T13:00:00Z","limit":"100"}
```

All shown keys are required. This tool returns event evidence plus a typed activity summary. The GUI displays the returned evidence, but currently has **no dedicated activity-count panel**. Use the [CLI activity example](cli-user-guide.md#account-activity-and-coverage) to inspect total events, returned events, successes, failures, hosts and coverage times.

### indicatorLookup

```json
{"indicator":"192.0.2.10","feedVersion":"demo-feed-v1"}
```

Both keys are required. In an ACCOUNT case, first retrieve event evidence containing that indicator. Alternatively create a case explicitly scoped to the indicator. The local feed loads from `data/indicators/threat-feed.json` at application startup when present. This is a local feed lookup, not an Internet threat-intelligence search.

### Evidence display contract

Starting Query clears the Evidence text area. Completion replaces it with **only that query's returned evidence**. For example, search with limit 6, then limit 2: the screen shows six records, then two. An EMPTY or UNKNOWN result shows no records; a failed query leaves the cleared display empty, so check progress to distinguish failure from no matches.

The display is a temporary view. Accepted evidence remains accumulated and deduplicated in the case, analysis and Report, even when a later query returns fewer or no records. Save and Export do not erase it. Opening a case, importing, changing scope, creating a case or running analysis resets the display to the case evidence view. The latest-query display selection is not persisted across a restart.

| Retrieval outcome | Meaning |
| --- | --- |
| SUCCESS | Query completed with matching evidence. |
| PARTIAL | The result was capped; more matches exist. Narrow filters or increase limit. |
| EMPTY | No stored events matched; this does not establish that an account is safe. |
| UNKNOWN | Indicator has no matching feed record; this is not a benign verdict. |
| STALE | Feed evidence exceeds its freshness limit. Inspect observedAt and limitations. |
| INVALID_ARGUMENT / OUT_OF_SCOPE | Invalid input or a query outside the case boundary. Correct the arguments or explicitly revise scope. |

## Result tabs

| Tab | Contents and interpretation |
| --- | --- |
| Plan | JSON plan with origin, revision, tool calls, string arguments and dependencies. `null` means no accepted plan. READY means it can be run, not that evidence has already been collected. |
| Evidence | Latest manual-query evidence, or accumulated case evidence outside that view. Each item retains an ID, source event/indicator ID, version, provenance and JSON payload. Read the display contract above. |
| Timeline | Assessed events in time order and candidate related pairs. Before analysis it says Not assessed. Relationships are not proof of causation. |
| Hypotheses | Accepted model explanations with evidence references and confidence. Confidence is qualitative, not a calibrated probability. |
| Assessment | Deterministic severity score/level/rules, completeness and recommendations. Model text cannot change the fixed score. Recommendations require analyst review; Sentinel does not execute containment actions. |
| Conversation | Saved follow-up questions, cited answers and failed-answer records. |
| History | Case UUID and chronological audit entries, including import counts, tool outcomes, planning and state changes. |
| Report | Read-only Markdown preview combining the case's accepted values and accumulated evidence. Export Markdown writes this report to a file. |

The text panes contain selectable text, not editable records or an HTML report viewer. Long evidence payloads wrap across lines. Scroll within the pane to inspect later items; rendering after an operation resets the scroll position.

## Follow-up questions

Type a question in the bottom field, then click **Ask**. Example: `What evidence supports this assessment?` Inspect Conversation for the answer and cited evidence IDs. Ask does not create a new case or modify scope.

Use `refresh: What evidence supports this assessment?` to explicitly request fresh, scope-validated collection before answering. Refresh may add evidence; inspect assessment completeness afterward. Questions requiring another target or time interval require an explicit scope revision. Requests can fail when the model is unavailable, citations are invalid or the requested scope is unsupported; the failure is retained in Conversation.

## Case status and completeness

| Case status | Meaning / next step |
| --- | --- |
| DRAFT | No ready plan, or import/scope changes invalidated it. Generate model plan or Approve rule plan. |
| READY | Validated plan accepted. Inspect it, then Run. |
| RUNNING | Run is executing. Wait or request Stop. |
| COMPLETED | Run accepted a complete assessment for the collected scope. Review its evidence and limitations. |
| PARTIAL | Some work or analysis is incomplete. Review History, progress and assessment limitations before deciding what to retry. |
| PAUSED | A saved RUNNING case was restored. Revalidate paused plan, then Run. |
| FAILED | Fatal run failure; inspect retained results. The case cannot retrieve, import or replan. Start a new case for another attempt. |

Assessment completeness is a separate value: COMPLETE, PARTIAL, STALE or UNAVAILABLE. New accepted evidence, revised scope or a changed dataset can leave old analysis STALE. A saved or exported report can contain partial/unassessed results; exporting does not make them complete.

Default execution ceilings are eight tool attempts, two plan revisions, six model calls, ten seconds per tool request and sixty seconds per model request, with one transient retry where supported. Analysis model context is capped at 24 evidence items and related pairs at 1,000. Limits can produce explicit partial results; inspect limitations instead of treating omitted data as absent activity. See [model setup](model-setup.md) for configuration constraints.

## Worked example with supplied data

1. Launch with `--dataset analyst_demo`; use a previously unused dataset for a clean first run.
2. Enter the example request above, Target `demo-analyst`, type ACCOUNT, Start `2026-10-22T12:00:00Z`, End `2026-10-22T13:00:00Z`, Feed `demo-feed-v1`, and leave Critical hosts blank.
3. Click New case. Check the planning outcome. If you intentionally want basic planning, choose the checkbox before creation or approve a rule plan explicitly.
4. Click Import JSON and select the supplied `data/scenarios/account-takeover.json`. A first import accepts six events into v1 and returns the case to DRAFT.
5. Run the logSearch example. Change only limit from `"6"` to `"2"` and query again: the Evidence tab now shows two records and progress says PARTIAL.
6. Run the indicatorLookup example. The returned feed payload contains verdict MALICIOUS, source and observedAt. Freshness depends on the current date; the supplied observation is October 26, 2026, and becomes stale after 30 days.
7. Generate model plan or Approve rule plan, inspect Plan, then click Run. Review Timeline, Hypotheses and Assessment. Exact model wording and completeness can vary.
8. Ask a cited follow-up, inspect Conversation, then Save and Export Markdown. Reopen the same UUID to continue later.

For the activity counts and equivalent commands, continue with the [CLI walkthrough](cli-user-guide.md#worked-example-on-windows).

## Troubleshooting

| Symptom | Check / recovery |
| --- | --- |
| MODEL_UNAVAILABLE | Run the setup command to start/warm Qwen and verify the connection. Then Generate model plan again. A basic rule plan is an explicit alternative for collection, but AI synthesis still needs the model. |
| INVALID_PLAN | The model draft failed validation. Inspect History and retry planning within available budgets, or explicitly approve basic rules. Strict validation remains in force. |
| ACTION: SUCCESS but planning was blocked | This means the action returned normally; inspect the preceding PLANNING outcome and actual Plan/status. It does not prove that a model plan was accepted. |
| Query shows fewer records than the case previously held | Expected: Evidence shows the latest query. Open Report to see retained case evidence. |
| Empty Evidence after Query | Check RETRIEVAL outcome: EMPTY/UNKNOWN are valid empty results, whereas an error indicates a failed request. |
| Imported data but Run is unavailable or rejected | Import changed the version and invalidated the plan. Generate or approve a plan for the new version. |
| Scope edits disappear | Fields were edited without a scope action, or the action was invalid for the state. Use Clarify scope for DRAFT or Revise completed scope for COMPLETED. |
| Cannot query a reopened case | Confirm the GUI was launched with that case's dataset and the original dataset files are available. |
| Export failed | Check destination permissions and the overwrite confirmation. Use an explicit `.md` filename. |
| Buttons do not respond immediately | A background operation is active. Read progress and wait for its bounded request to finish. Stop applies to Run at operation boundaries. |

The GUI does not currently provide an in-app help menu, configuration editor, query-result summary panel or general undo/delete controls. This document is the external help reference. See the [CLI guide](cli-user-guide.md) for typed results and diagnostics, and [model setup](model-setup.md) for configuration.
