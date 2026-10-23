# AI-human collaboration log

This log summarizes representative interactions rather than the full conversation. The accepted Stage 1 design guides implementation. Yegor directs scope and implementation order, reviews generated code, performs local verification, and approves changes before publication. On 2026-10-22, Yegor confirmed that he reviews and tests all delivered code and approved Step 4 for publication. Automated results below were recorded by the assistant; human review and testing are recorded from Yegor's confirmation.

## 2026-10-20 - Initial Stage 2 skeleton

- Task/instruction: Establish project structure and deliverable placeholders from the accepted design and course requirements.
- AI contribution: Reviewed assignment documents and design diagrams; created the approved Java scaffold, configuration templates, ignore rules and demo-storage layout.
- Human contribution: Established the design baseline, selected the scope, reviewed the scaffold and approved its incorporation and publication.
- Outcome: A structured starting point for incremental implementation. Business features remained pending.

## 2026-10-20 - Step 1 build and configuration

- Task/instruction: Implement configuration and build support with focused tests and reproducible manual checks.
- AI contribution: Implemented immutable configuration loading, validation, startup diagnostics, Maven Wrapper and 17 MVP tests; diagnosed local build-output restrictions.
- Human contribution: Directed incremental development, reviewed and tested the delivered code, requested an in-place build retry, and approved publication.
- Outcome: Configuration and bootstrap support incorporated. The later in-place Maven retry passed all 17 tests without changing folder protection. Feature videos are planned after integration.

## 2026-10-20 - Step 2 core domain contracts

- Task/instruction: Implement core data contracts while preserving Stage 1 semantics.
- AI contribution: Implemented CaseScope, Request, Event and ImportBatch invariants, defensive copying, 23 domain tests and manual verification instructions.
- Human contribution: Reviewed and tested the delivered code before approving publication; maintained the requirement to implement small, reviewable increments.
- Outcome: In-place verification passed all 40 tests. Goal-only requests can await clarification; immutable import batches retain duplicates for later import processing.

## 2026-10-21 - Step 3 case lifecycle and snapshots

- Task/instruction: Implement the Stage 1 lifecycle and snapshot foundation.
- AI contribution: Implemented guarded transitions, immutable snapshots, in-memory restoration and 25 lifecycle tests. Added the original analyst goal to snapshots and documented the rationale.
- Human contribution: Reviewed and tested the delivered code within the accepted design and approved publication.
- Outcome: In-place verification passed all 65 tests. Restoring a running case yields a paused case without executing it. File persistence remains deferred.

## 2026-10-22 - Step 4 validated dataset import

- Task/instruction: Implement validated import and atomic dataset updates, with synthetic examples and reproducible checks.
- AI contribution: Implemented bounded JSON validation, UTC normalization, versioned ingestion, duplicate/conflict handling, 31 import tests, synthetic samples and a packaged-JAR demo.
- Human contribution: Reviewed and tested the implementation, confirmed review completion, and approved publication. Requested clearer collaboration records and cost accounting, and separation of personal planning aids from submission materials.
- Outcome: In-place Maven verification passed all 96 tests. The demo accepted two events and one duplicate; reimport counted three duplicates; conflicting and invalid batches preserved version v1 with two events.
- Remaining scope: Controller integration, case audit/staleness updates and durable storage are pending.

Tool/model and cost details are recorded in [AI tools, models and costs](ai-tools-and-costs.md). Commands and evidence are recorded in [manual verification](manual-verification.md) and [verification log](verification-log.md).
