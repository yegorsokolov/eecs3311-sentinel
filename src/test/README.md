# Test scaffold

JUnit Jupiter tests cover configuration defaults, local-file/process overrides, validation failures, path resolution and bootstrap argument handling. Run `.\mvnw.cmd test` or `.\mvnw.cmd verify`; inspect `target/surefire-reports/` for results. These MVP checks do not yet test investigation features.

Step 2 adds CoreDomainTest: required scope/event data, interval boundaries, unscoped requests awaiting clarification, preserved optional unknowns and immutable import batches. Current total: 40 passing cases, including 23 core-domain cases. Run only those with `.\mvnw.cmd "-Dtest=CoreDomainTest" test`.

Step 3 adds InvestigationTest: guarded lifecycle transitions, rejection without mutation, immutable snapshots including nested collections, schema/reference checks and restored RUNNING cases becoming PAUSED. Run with `.\mvnw.cmd "-Dtest=InvestigationTest" test`. Current execution totals are recorded in docs/verification-log.md; the Step 2 total above is historical.

Prioritize atomic import and conflicting IDs, scoped tool validation, budget limits, severity boundaries, timeline ordering, citation rejection, snapshot restoration and GUI/CLI parity. Keep deterministic tests independent of a running model. Full deterministic coverage and KUMA validation are Stage 3 work.
