# Verification summary

| Implementation | Recorded result |
| --- | --- |
| Configuration/startup | 17 tests passed |
| Core contracts | 40 cumulative tests passed |
| Lifecycle/snapshots | 65 cumulative tests passed |
| Dataset import | 96 cumulative tests passed, zero failures/errors/skips |
| Filtered log search | 116 cumulative tests passed, zero failures/errors/skips |
| Account activity | 140 cumulative tests passed, zero failures/errors/skips |
| Indicator lookup | 181 cumulative tests passed, zero failures/errors/skips |
| Controlled tool execution | 212 cumulative tests passed, zero failures/errors/skips |
| Active-case evidence/import integration | 245 cumulative tests passed, zero failures/errors/skips |

Previous verification: 2026-10-22, JDK 17, Maven Wrapper `verify`. The packaged-JAR import demo accepted two events and one duplicate; reimport counted three duplicates. Conflicting and invalid batches preserved version v1 with two events. The packaged search demo returned SUCCESS (2 events), PARTIAL (1 capped event), EMPTY and INVALID_ARGUMENT as expected, leaving v1/2 unchanged. A copy of .env.example loaded successfully as .env in a temporary directory using the bundled defaults; the project .env remains empty. Configuration diagnostics also passed. Reproduction commands are in the project README.

The initial local build-output restriction was resolved on retry without changing folder protection. I reviewed and tested the code, and checked its compliance with the Stage 1 design, before approving Step 4 publication. I also reviewed and tested Step 5 and approved its publication.

Remaining limitations: GUI/CLI investigation flows, model integration, investigation orchestration, plan validation and durable storage are pending. Full deterministic and KUMA validation belong to Stage 3.

Previous verification: 2026-10-26, JDK 17, Maven Wrapper verify: 140 tests passed (24 new account-activity tests), zero failures/errors/skips. The packaged-JAR activity demo returned SUCCESS with demo-1/demo-2, one success/one failure/one host; PARTIAL with one returned/two total; EMPTY with zero counts and explicit interval; INVALID_ARGUMENT for limit zero. Dataset v1/two events remained unchanged. JShell required an unsandboxed retry for Windows Java preferences access. I reviewed and tested Step 6, then approved its commit and push on October 27.

Previous verification: 2026-10-27, JDK 17, Maven Wrapper verify: 181 tests passed (41 new indicator-lookup checks), zero failures/errors/skips. The packaged-JAR indicator demo loaded demo-feed-v1, returned SUCCESS/MALICIOUS for the example IP, STALE/SUSPICIOUS for the normalized old domain, UNKNOWN with no evidence for an absent indicator, INVALID_ARGUMENT for a URL and FEED_UNAVAILABLE with no loaded feed. Event data remained v0/empty. Demo time is fixed to October 27; production defaults use the UTC system clock. JShell used the previously required unsandboxed Windows preferences access. I reviewed and tested Step 7, then approved its commit and push on October 27.

Latest verification: 2026-10-28, JDK 17, Maven Wrapper verify: 212 tests passed (31 new controlled-execution checks), zero failures/errors/skips. The packaged-JAR executor demo returned scoped SUCCESS/two events, DUPLICATE_CALL, OUT_OF_SCOPE, PARTIAL account activity with one returned/two total and SUCCESS/MALICIOUS for an observed indicator. Three attempts left five remaining; a separate session refused attempt nine after eight accepted submissions. Dataset v1/two events remained unchanged. Tests also verified retry charging, interrupted callers, timeout cancellation with an interrupt-ignoring command, close cancellation and concurrent duplicate suppression. JShell used the established Windows preferences access. I reviewed and tested Step 8, then approved its commit and push on October 28.


Step 9 publication verification — October 29, 2026: the isolated Step 9 snapshot passed Maven Wrapper verify with JDK 17: 245 tests, zero failures/errors/skips. Its 33 new checks comprise 14 evidence aggregate checks and 19 application flow checks. Two later planning-related application checks are intentionally excluded until Step 10. The student confirmed Step 9 was reviewed and tested and authorized publication. Later implementation remains outside this snapshot.

The isolated packaged-JAR case walkthrough passed: import v1 accepted two events/one duplicate; search attached two items; enrichment added one with two session attempts; overlapping activity retained three unique items; duplicate import preserved READY; new import selected v2/DRAFT and cleared the fixture plan while retaining evidence; old session returned OUT_OF_SCOPE; invalid import preserved v2; audit references were present. JShell required Windows preferences access outside the sandbox.


Step 10 publication verification — October 29, 2026: isolated JDK 17/Maven Wrapper verify passed 272 tests, zero failures/errors/skips. Relative to the published Step 9 snapshot, this adds 25 PlanningTest cases and two ApplicationFlowTest boundary checks for same-draft clarification/approval and independent engine validation. The student explicitly confirmed Step 10 review/testing and authorized publication. Later model and completion changes are not included.

The packaged planning demo passed clarification, explicit RULE approval, dependency-aware next-step selection, invalid scope/dependency rejection and revision-ceiling refusal without retrieval. The case-integration demo passed canonical attachment, same-session enrichment, duplicate preservation, plan/version/session invalidation, rejected import and audit references.


Step 11 publication verification — October 30, 2026: isolated JDK 17/Maven Wrapper verify passed **309 tests, zero failures/errors/skips**, including 37 OllamaAdapterTest cases. Fake loopback servers cover request escaping/options, status classification, malformed/duplicate/truncated envelopes, strict UTF-8, size limits, header/body deadlines, interruption and unavailable ports; concurrent budget checks enforce six calls. The packaged demos/ollama-check.jsh received a real qwen2.5:7b completion (20 characters) from the existing local service. No configuration/secrets were changed. The student confirmed review/testing and authorized publication. Model planning, optional compatible-provider setup and Steps 12–21 remain excluded.
