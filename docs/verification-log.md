# Development and verification log

## Initial scaffold - 2026-10-20

No business-feature tests or runtime-model integration have been completed.

| Date | Check / change | Result and evidence | Defect / correction | Remaining limitations |
| --- | --- | --- | --- | --- |
| 2026-10-20 | Compile all 57 Java sources using JDK 17 with `--release 17` | Passed with an in-memory JavaCompiler file manager | Normal class-file output was blocked by the environment; in-memory output avoided the write restriction | Maven build not run; Maven is absent from PATH |
| 2026-10-20 | Invoke `sentinel.Main --cli` after compilation | Passed; printed the explicit skeleton notice | None | GUI and CLI functionality remain pending |
| 2026-10-20 | Parse five JSON fixtures and `pom.xml` | Passed; all fixtures are empty arrays and Maven XML is well formed | None | No schema or real dataset validation yet |
| 2026-10-20 | Inspect local `.env` | Exactly zero bytes | None | Configuration loading pending |
| 2026-10-20 | `git check-ignore --no-index` for `.env`, `.env.local`, generated case JSON and class output | All correctly ignored | Used bundled Git because the PATH Git process stalled | No commits or pushes performed |

Record actual commands and outcomes. Compiling a scaffold does not establish feature completion.

## Step 1 - build and configuration (2026-10-20)

- Reread Stage 1 sections on local model integration, scope, interfaces, bounded autonomy and design decisions. Preserved Ollama/Qwen2.5, JSON storage and the 8/2/6/10/60/1 limits.
- Official Maven Wrapper 3.3.2 with Maven 3.9.9 reached Maven successfully. In-place verification failed because Java could not create target/classes in the OneDrive project directory.
- Copied pom.xml and src unchanged to a temporary directory and ran Maven 3.9.9 verify with JDK 17.0.9: BUILD SUCCESS. 17 tests, zero failures/errors/skips (15 configuration cases and 2 bootstrap cases); JAR packaged successfully.
- Ran that JAR from the original project directory: --check-config printed the intended Stage 1 defaults and resolved paths. Invalid process URL returned a concise error and exit 2.
- Confirmed .env remains zero bytes and git diff --check passes. The OneDrive output restriction remains an environment limitation, not a passing in-place build claim.
- Manual review and local verification by the user are pending; follow manual-verification.md. Videos are deferred until final feature integration. No commit/push performed for this step.

### Step 1 environment retry

The later requested in-place retry succeeded: Maven verify passed all 17 tests and produced the project-local JAR. No folder-protection changes were made. The previous write limitation is historical, not a current blocker.

## Step 2 - core domain contracts (2026-10-20)

- Re-read Stage 1 section 10 and F01 clarification behavior. Implemented constructor checks for CaseScope, Request and Event, plus defensive ImportBatch copying.
- A null Request.scope represents a request awaiting clarification; a constructed CaseScope is complete and valid. Draft lifecycle and planning guards remain pending.
- Event optional account/IP/host/outcome remain nullable. Source positions and dataset versions are required; identifiers are preserved rather than silently normalized.
- In-place command: Maven Wrapper --batch-mode --no-transfer-progress verify with JDK 17.
- Result: BUILD SUCCESS, 40 tests, zero failures/errors/skips; 23 new domain cases and 17 existing cases. JAR packaged in the original OneDrive project's target directory.
- Covered valid/boundary/reversed scopes, missing fields, unknown optional event details, provenance, immutable collection copying, null entries and duplicate preservation for later import handling.
- Manual verification pending; exact commands and JShell flow are in manual-verification.md. No commit or push performed.
