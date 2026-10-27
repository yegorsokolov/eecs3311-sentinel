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

Previous verification: 2026-10-22, JDK 17, Maven Wrapper `verify`. The packaged-JAR import demo accepted two events and one duplicate; reimport counted three duplicates. Conflicting and invalid batches preserved version v1 with two events. The packaged search demo returned SUCCESS (2 events), PARTIAL (1 capped event), EMPTY and INVALID_ARGUMENT as expected, leaving v1/2 unchanged. A copy of .env.example loaded successfully as .env in a temporary directory using the bundled defaults; the project .env remains empty. Configuration diagnostics also passed. Reproduction commands are in the project README.

The initial local build-output restriction was resolved on retry without changing folder protection. I reviewed and tested the code, and checked its compliance with the Stage 1 design, before approving Step 4 publication. I also reviewed and tested Step 5 and approved its publication.

Remaining limitations: GUI/CLI investigation flows, model integration, tool execution, active-case import integration and durable storage are pending. Full deterministic and KUMA validation belong to Stage 3.

Previous verification: 2026-10-26, JDK 17, Maven Wrapper verify: 140 tests passed (24 new account-activity tests), zero failures/errors/skips. The packaged-JAR activity demo returned SUCCESS with demo-1/demo-2, one success/one failure/one host; PARTIAL with one returned/two total; EMPTY with zero counts and explicit interval; INVALID_ARGUMENT for limit zero. Dataset v1/two events remained unchanged. JShell required an unsandboxed retry for Windows Java preferences access. I reviewed and tested Step 6, then approved its commit and push on October 27.

Latest verification: 2026-10-27, JDK 17, Maven Wrapper verify: 181 tests passed (41 new indicator-lookup checks), zero failures/errors/skips. The packaged-JAR indicator demo loaded demo-feed-v1, returned SUCCESS/MALICIOUS for the example IP, STALE/SUSPICIOUS for the normalized old domain, UNKNOWN with no evidence for an absent indicator, INVALID_ARGUMENT for a URL and FEED_UNAVAILABLE with no loaded feed. Event data remained v0/empty. Demo time is fixed to October 27; production defaults use the UTC system clock. JShell used the previously required unsandboxed Windows preferences access. I reviewed and tested Step 7, then approved its commit and push on October 27.
