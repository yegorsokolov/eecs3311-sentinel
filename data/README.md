# Demo data placeholders

All JSON files contain empty arrays, not usable demo data.

- `samples/account-takeover.json`: planned failed-login burst, later success and malicious source IP; expected rule score 7 before any critical-asset point.
- `samples/benign-activity.json`: planned ordinary authentication activity.
- `samples/incomplete-evidence.json`: planned missing context or insufficient authentication evidence.
- `indicators/threat-feed.json`: synthetic indicators, verdicts, source dates and feed versions.
- `inventory/assets.json`: synthetic host criticality records.

Use synthetic accounts, example domains and reserved example IPs. Preserve source positions and dataset versions. JSON runtime storage belongs under `runtime/`; no SQL database is required by the design.
