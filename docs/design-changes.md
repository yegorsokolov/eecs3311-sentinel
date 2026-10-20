# Design changes and rationale

No feature or pattern changes approved. Stage 1 remains the baseline.

Step 2 implements section 10 contracts. A complete CaseScope is validated at construction; Request permits a null scope so F01 can later leave incomplete requests in DRAFT pending clarification. Event preserves nullable optional details and requires source provenance. ImportBatch copies its list but does not deduplicate; import/store logic will handle duplicate counts and conflicts. DomainValidation is a package-private helper, not an additional design pattern. Remaining record invariants will be implemented alongside their features.

Step 1 adds implementation support: Maven Wrapper, typed AppConfig/ExecutionLimits, a configuration exception, and --help/--check-config diagnostics. These preserve the Stage 1 model choice, storage approach and execution limits. The diagnostics do not count toward the feature minimum; budgets are loaded here and will be enforced by AgentEngine later. Optional environment overrides and dotenv parsing are implementation details absent from the design diagrams.

Scaffolding choices: Maven layout, Java 17 source target, package organization and a bootstrap entry point. These organize implementation without replacing the designed features. Typed records are provisional and require invariant validation before use.

| Original design / feature / pattern | Implemented change | Reason | Impact on use cases, classes, methods, patterns and user need | Approval / evidence |
| --- | --- | --- | --- | --- |
| Pending | Pending | Pending | Pending | Pending |
