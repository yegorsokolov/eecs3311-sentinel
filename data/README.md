# Demo data and placeholders

The following synthetic files exercise the implemented import layer:

- `samples/import-valid.json`: two unique login events and one identical duplicate with an equivalent UTC timestamp.
- `samples/import-conflict.json`: a new event followed by conflicting content for demo-1; run after the valid sample to verify whole-batch rollback.
- `samples/import-invalid.json`: a timestamp missing its UTC offset; expected validation failure.
- `samples/import-additional.json`: a third synthetic event; used after import-valid.json to advance the active case to v2 and invalidate its old plan/session.

After packaging, run `jshell --class-path target/sentinel-0.1.0-SNAPSHOT.jar demos/import-demo.jsh` from the project root.

The indicator lookup fixture is `indicators/threat-feed.json`: a versioned object containing synthetic IPv4, domain and IPv6 records. It includes a recent malicious IP, an old suspicious domain and a recent benign IPv6 address. Run `demos/indicator-demo.jsh` against the packaged JAR for repeatable fresh/stale/unknown/error results using a fixed review clock. See the [GUI guide](../docs/gui-user-guide.md#indicatorlookup) and [CLI guide](../docs/cli-user-guide.md#enrich-an-observed-indicator) for lookup arguments and result interpretation.

The following remain empty-array placeholders:

- `samples/account-takeover.json`: planned failed-login burst, later success and malicious source IP; expected rule score 7 before any critical-asset point.
- `samples/benign-activity.json`: planned ordinary authentication activity.
- `samples/incomplete-evidence.json`: planned missing context or insufficient authentication evidence.
- `inventory/assets.json`: synthetic host criticality records.

Use synthetic accounts, example domains and reserved example IPs. Preserve source positions and dataset versions. JSON runtime storage belongs under `runtime/`; no SQL database is required by the design.

`data/scenarios/account-takeover.json` contains five failed logins followed by success from the feed-matched synthetic IP (score 7 HIGH; 8 with workstation-1 critical). `data/scenarios/benign.json` supplies a sparse success-only alternative. No scenario contains real account data or model responses.
