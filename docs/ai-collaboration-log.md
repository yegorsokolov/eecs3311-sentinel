# AI-human collaboration log

I used the accepted Stage 1 design to guide development. For the increments I reviewed, I checked the generated code against that design, tested it locally, and decided when it was ready to publish. I also reviewed and organized the documentation. Codex helped write code, run automated checks, and draft documentation. The entries below separate that assistance from my own work; test counts refer to the automated checks.

## 1. Establishing a design-led development process — October 20

**Task and instruction:** Create a local Java project and deliverable scaffold consistent with the accepted Stage 1 design and Stage 2 requirements. Preserve the existing architecture and defer the final report until implementation is ready.

**AI contribution:** Reviewed the assignment documents, Stage 1 report and diagrams; mapped the planned classes to packages; created build/configuration placeholders, demo-data locations and submission-record templates.

**My contribution:** I supplied my Stage 1 design and checked that the project structure followed it. I chose the initial scope and reviewed the scaffold before approving it.

**Outcome:** The project began with a design-aligned structure. Scaffold code was treated as incomplete functionality rather than presented as a finished application.

## 2. Build and configuration — October 20

**Task and instruction:** Implement the first small development increment and provide commands and expected outcomes for local verification.

**AI contribution:** Added immutable configuration loading and validation, startup diagnostics, Maven Wrapper and 17 automated tests. Investigated a build-output restriction affecting the local project directory.

**My contribution:** I worked through the changes in small steps, reviewed and tested the code, and checked it against Stage 1. When the build hit a local output-directory problem, I asked for another attempt in the project folder. I kept checking each increment while leaving the final videos until integration.

**Outcome:** The in-place retry succeeded without changing folder protection. The configuration/build foundation passed 17 tests and was published after approval. This established a repeatable verification workflow for later changes.

## 3. Core domain contracts — October 20

**Task and instruction:** Implement the next focused increment using the Stage 1 data contracts.

**AI contribution:** Added validation for CaseScope, Request and Event, defensive copying for ImportBatch, and 23 domain tests. Preserved incomplete requests for later clarification and retained duplicates for the import layer to process.

**My contribution:** I reviewed and tested the domain code and checked its compliance with the Stage 1 design before approving it.

**Outcome:** Core contracts were incorporated with 40 cumulative passing tests. The changes provided validated inputs for subsequent lifecycle and import work.

## 4. Case lifecycle and immutable snapshots — October 21

**Task and instruction:** Implement the next lifecycle increment while preserving the accepted state model.

**AI contribution:** Added guarded state transitions, immutable snapshots, in-memory restoration and 25 lifecycle tests. Corrected the scaffold's omission of the original analyst goal from snapshots. Restoration of a running snapshot produces a paused case without executing it.

**My contribution:** I reviewed and tested the lifecycle implementation, checked it against the Stage 1 design, and approved it for publication.

**Outcome:** The lifecycle foundation passed 65 cumulative tests. Disk persistence and execution orchestration remained explicitly pending; the snapshot correction supported the existing design.

## 5. Validated dataset import — October 22

**Task and instruction:** Implement the next step with MVP tests and a reproducible local review flow.

**AI contribution:** Implemented bounded JSON validation, UTC normalization, atomic ingestion, immutable dataset versions and duplicate/conflict handling. Added 31 tests, synthetic input files and a packaged-JAR demonstration.

**My contribution:** I reviewed and tested the import implementation and checked it against Stage 1. Once I had finished that review, I approved the push.

**Outcome:** All 96 tests passed. The demo accepted two unique events and one duplicate; reimport counted three duplicates. Conflicting content and an invalid timestamp rejected their batches without changing the two-event dataset. Active-case integration and durable storage remain pending.

## 6. Reviewing submission evidence and repository organization — October 22

**Task and instruction:** Make the documentation concise, accurate and relevant to the submission, while keeping personal working notes separate.

**AI contribution:** Checked required report content, updated the weekly commit summary with published commit links, consolidated operational guidance into the README, and separated internal notes from submission records.

**My contribution:** I reviewed the submission requirements, organized the documentation, and checked the commit summary. I also clarified how I use my subscription for development and the local model for Sentinel.

**Outcome:** Required collaboration, tool/cost, verification, feature/pattern and demonstration records remain available. Internal planning and detailed review notes are kept under an ignored local-notes directory. Documentation-only edits are excluded from the meaningful-code commit count.

## 7. Planning substantive increments and implementing log search — October 22

**Task and instruction:** Implement filtered log search as the next testable increment and provide a copy-ready environment template. Plan the remaining work around feature dependencies and the course schedule.

**AI contribution:** Prepared an internal milestone plan, implemented versioned filtered log search with typed errors and immutable evidence, added 20 tests and a component demo, and clarified all five supported environment variables and defaults.

**My contribution:** I asked for a plan that follows the dependencies between features and leaves time to review each increment. I also asked for a configuration template that someone can copy and fill in. I reviewed and tested this increment, then approved it for publication.

**Outcome:** Automated verification passed 116 tests. Executor/case integration remains pending. I approved the commit and push after completing my review and testing.

See [AI tools and costs](ai-tools-and-costs.md), [verification summary](verification-log.md) and [weekly commit summary](weekly-commit-summary.md) for supporting records.
