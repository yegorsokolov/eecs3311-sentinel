# Design changes and rationale

No feature or pattern changes approved. Stage 1 remains the baseline.

Step 3 implements the SM01 lifecycle and immutable aggregate snapshots. Construction, clarifyScope, reviseScope, and restore are concrete entry points for behaviors already described in F01/F11; STATE_CHANGED audit entries use CaseStatus names. setPlan is the trusted application boundary after semantic plan validation and does not itself validate registry membership or call arguments. FAILED remains terminal as drawn in SM01. A reopened RUNNING snapshot becomes PAUSED and requires explicit revalidation before Run.

The skeleton omitted the original goal from CaseSnapshot. A goal field now preserves it, matching Stage 1's request/context requirements; there are no previously persisted JSON cases to migrate. caseVersion denotes an in-memory revision, starting at zero and incremented once per successful mutation; save/conflict semantics are deferred to the repository step. Model/prompt/policy versions remain null until known instead of being invented.

Nested snapshot records now defensively copy collections. Analysis-specific semantic checks, tool-result ingestion, plan scheduling and canonical tool keys remain with their respective later steps. No new feature or pattern is claimed.

Step 2 implements section 10 contracts. A complete CaseScope is validated at construction; Request permits a null scope so F01 can later leave incomplete requests in DRAFT pending clarification. Event preserves nullable optional details and requires source provenance. ImportBatch copies its list but does not deduplicate; import/store logic will handle duplicate counts and conflicts. DomainValidation is a package-private helper, not an additional design pattern. Remaining record invariants will be implemented alongside their features.

Step 1 adds implementation support: Maven Wrapper, typed AppConfig/ExecutionLimits, a configuration exception, and --help/--check-config diagnostics. These preserve the Stage 1 model choice, storage approach and execution limits. The diagnostics do not count toward the feature minimum; budgets are loaded here and will be enforced by AgentEngine later. Optional environment overrides and dotenv parsing are implementation details absent from the design diagrams.

Scaffolding choices: Maven layout, Java 17 source target, package organization and a bootstrap entry point. These organize implementation without replacing the designed features. Typed records are provisional and require invariant validation before use.

| Original design / feature / pattern | Implemented change | Reason | Impact on use cases, classes, methods, patterns and user need | Approval / evidence |
| --- | --- | --- | --- | --- |
| Pending | Pending | Pending | Pending | Pending |
