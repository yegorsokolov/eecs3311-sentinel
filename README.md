# Sentinel

AI cybersecurity incident triage and investigation agent for EECS 3311 Software Design.

## Current status

Stage 2 skeleton only. F01-F12 are not implemented. Contracts and dependency boundaries follow the accepted Stage 1 report and UML diagrams. Business methods explicitly throw `UnsupportedOperationException`. The entry point prints a skeleton notice; no working GUI, CLI investigation commands, model integration, import or persistence is claimed.

## Design

Java with a Swing GUI and CLI sharing `AgentController`; bounded orchestration through `AgentEngine`; local Qwen2.5 7B Instruct through Ollama; JSON snapshots and local datasets. Planned patterns: MVC, Strategy, Command, Observer and Adapter. The existing report and `UML Diagrams/` remain the design baseline.

## Prerequisites and build

- JDK 17 or newer; source compatibility targets Java 17.
- Maven 3.9 or newer on PATH. No Maven wrapper is bundled yet.
- Ollama and its model will be needed when integration is implemented. The skeleton makes no model calls.

From the project root:

```powershell
mvn compile
mvn test
mvn package
java -jar target/sentinel-0.1.0-SNAPSHOT.jar
```

Maven resolves the pinned plugins and JUnit dependency. There are currently zero automated tests. Main source uses only JDK APIs; JSON and CLI dependencies will be selected when those components are implemented.

## Local configuration

The local `.env` is deliberately completely empty and ignored by Git. `.env.example` contains blank optional settings and no credentials. For a fresh clone, copy it only if `.env` does not already exist:

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
```

Configuration loading is pending. Planned defaults in `src/main/resources/application.properties` use `http://localhost:11434`, model `qwen2.5:7b`, and storage under `runtime/`. Local Ollama needs no API key. If a future provider requires a key, enter it only in ignored local configuration and document the variable in the safe example. Never place secrets in source, snapshots, logs, videos or reports.

## GUI and CLI launch contracts

These commands currently print the skeleton notice; the interfaces are pending:

```powershell
java -jar target/sentinel-0.1.0-SNAPSHOT.jar --gui
java -jar target/sentinel-0.1.0-SNAPSHOT.jar --cli
```

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

`data/` holds empty JSON placeholders and scenario notes, not a working demo. `runtime/cases`, `runtime/datasets`, `runtime/exports` and `runtime/logs` hold generated local files; only directory markers are retained in Git. JSON persistence needs no SQL service.

## Development records

See `docs/feature-implementation-mapping.md`, `docs/design-patterns.md`, `docs/design-changes.md`, `docs/ai-tools-and-costs.md`, `docs/ai-collaboration-log.md`, `docs/verification-log.md`, `docs/weekly-commit-summary.md`, `docs/demonstration-index.md` and `docs/submission-checklist.md`. Test planning is in `src/test/README.md`.

The Stage 2 report is intentionally excluded. Commits and pushes require the user's explicit permission.
