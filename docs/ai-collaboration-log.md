# AI-human collaboration log

## 2026-10-21 - Step 3 case lifecycle and snapshots

- Task/instruction: Implement the next incremental development step using the accepted Stage 1 design.
- AI contribution: Reviewed SM01, F11 and UC06/UC07; implemented guarded aggregate transitions, immutable snapshots, in-memory restoration and lifecycle tests; documented manual JShell checks.
- Human contribution: Authorized the next step; established the Stage 1 source-of-truth and review-before-commit requirements. Human code review remains pending.
- Design detail: Added the analyst goal omitted from the initial snapshot scaffold. Kept plan/tool/analysis semantic validation in their planned services and file persistence deferred. RUNNING restores as PAUSED without executing.
- Outcome: See verification-log.md for actual results. No commit or push authorized for this step.
- Tools/models and usage: Codex desktop; see ai-tools-and-costs.md. Unavailable token/cost data is not treated as zero.

## 2026-10-20 - Initial Stage 2 skeleton

- Task: Establish project structure aligned with the accepted Stage 1 design and Stage 2 deliverables.
- Input/instruction: Create code/deliverable placeholders, an empty `.env`, safe configuration, ignore rules and demo-storage placeholders; exclude the Stage 2 report. Obtain approval before local creation; no commits or pushes.
- AI contribution: Reviewed local assignment PDFs, Stage 1 report and class diagrams; checked GitHub metadata and README; proposed and created the approved scaffold.
- Human contribution: Confirmed the accepted design needs no changes, specified scope and Git restrictions, and explicitly approved local creation. Review of generated code is pending.
- Outcome: Scaffold only; F01-F12 remain pending. See `verification-log.md` for actual checks.
- Access limitation: ChatGPT project listing was visible, but uploaded contents could not be read. Local copies supplied requirements.

## Next representative interaction

## 2026-10-20 - Step 1 build and configuration

- Task/instruction: Re-review Stage 1 as the source of truth; implement build/configuration with MVP tests and precise manual verification instructions. Defer videos until the end.
- AI contribution: Implemented immutable configuration loading and validation, startup help/configuration diagnostics, official Maven Wrapper setup, 17 MVP test cases and documentation.
- Human contribution: Chose incremental implementation/review and final video timing; instructed adherence to Stage 1. Review of this step is pending.
- Outcome: Configuration and bootstrap support implemented; no investigation feature declared complete. Maven verify passed in a temporary copy; in-place Java output remains blocked by the environment. Exact checks and limitation recorded in verification-log.md.
- Development tool/model: Codex desktop; see ai-tools-and-costs.md. Token/money costs unavailable, not assumed zero.
- Commit/push: Not authorized for this step and not performed.

## Next representative interaction template

## 2026-10-20 - Step 2 core domain contracts

- Task/instruction: Implement the next planned step in a small reviewable chunk, using Stage 1 as source of truth.
- AI contribution: Re-read core data contracts; implemented CaseScope, Request, Event and ImportBatch invariants with a package-private validation helper; added 23 domain test cases and manual review instructions.
- Human contribution: Authorized the next step and established the review-before-commit workflow. Review of this code remains pending.
- Design interpretation: A goal-only Request can await clarification, while CaseScope represents complete validated scope. ImportBatch is immutable but retains duplicates for the later importer to count. Other record contracts are deferred to their owning features.
- Outcome: In-place Maven verification passed all 40 tests and packaged the JAR. No case lifecycle, tool execution or import behavior is claimed complete.
- Tool/model and costs: See ai-tools-and-costs.md; unavailable usage is not treated as zero.
- Commit/push: Not performed for Step 2.

## Next representative interaction template

- Date / task:
- Tool / model:
- Input/instruction:
- AI contribution:
- Human review, correction, rejection or improvement:
- Incorporated outcome:
- Verification / related files:
