# Sentinel

AI cybersecurity incident triage and investigation agent for EECS 3311 Software Design.

## Current status

Steps 1-4 implement build/configuration diagnostics, core contracts, the in-memory case lifecycle/snapshots, and validated JSON import into an atomic versioned dataset store. F01-F12 are not yet complete as user-facing workflows. GUI, investigation CLI commands, model integration, active-case import wiring, tool execution and JSON file persistence remain pending.

## Design

Java with a Swing GUI and CLI sharing `AgentController`; bounded orchestration through `AgentEngine`; local Qwen2.5 7B Instruct through Ollama; JSON snapshots and local datasets. Planned patterns: MVC, Strategy, Command, Observer and Adapter. The existing report and `UML Diagrams/` remain the design baseline.

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

Stage 1 budgets remain 8 tool attempts, 2 plan revisions, 6 model calls, 10/60-second tool/model timeouts and one transient retry. Configuration exposes these bundled defaults; AgentEngine will enforce them in a later step.

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

This reads the synthetic files without changing them or writing case data. See `docs/event-import-contract.md` for fields, bounds, duplicate handling and version semantics. See `docs/manual-verification.md` for expected output and tests.

## Development records

See `docs/feature-implementation-mapping.md`, `docs/design-patterns.md`, `docs/design-changes.md`, `docs/ai-tools-and-costs.md`, `docs/ai-collaboration-log.md`, `docs/verification-log.md`, `docs/weekly-commit-summary.md` and `docs/demonstration-index.md`. Test planning is in `src/test/README.md`.

The Stage 2 report is intentionally excluded. Commits and pushes require the user's explicit permission. Record feature videos after implementation is complete; perform the checks in `docs/manual-verification.md` after each development step.
