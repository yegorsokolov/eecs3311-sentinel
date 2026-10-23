# Event import contract

Stage 1 F02/UC02/SD02 requires validation before atomic ingestion.

## Input

One UTF-8 JSON array of objects (an optional UTF-8 BOM is accepted). A source must be a regular file. Defaults: at most 10 MiB and 100,000 array entries, including duplicates. Individual strings are limited to approximately 65,536 characters by the JSON parser.

| Field | Required | Type / meaning |
| --- | --- | --- |
| eventId | Yes | Nonblank string; stable unique event identifier |
| timestamp | Yes | ISO-8601 string with Z or an explicit numeric UTC offset |
| eventType | Yes | Nonblank string; no event-type enumeration is imposed yet |
| accountId | No | String or null |
| sourceIp | No | String or null; IP syntax is validated by relevant tools later |
| hostId | No | String or null |
| outcome | No | String or null |

Missing/null/blank optional values become null (unknown). Other text preserves case and content. Timestamps become Instant values on the UTC timeline. IDs are compared exactly; no silent case folding or trimming is performed.

Unknown fields, duplicate object keys, non-string values (except null), nested field values, malformed timestamps, malformed JSON and trailing JSON documents reject the file. Client-supplied sourcePosition/datasetVersion fields are not accepted: provenance is derived by the importer/store.

## Atomic ingestion and versioning

LogImporter reads within the byte bound, validates every row, then calls DatasetStore.ingest once. Validation failures return Summary with errors and zero accepted/duplicate counts. No part of a rejected batch is visible.

A DatasetStore represents one named dataset. Its initial version v0 is empty. A successful import containing new events publishes v1, v2, etc. Empty imports and duplicate-only reimports keep the current version. Successful summary counts distinguish newly accepted records from identical duplicates.

Duplicate comparison uses event ID, normalized timestamp, event type, account, IP, host and outcome. Different source files or row positions do not turn otherwise identical events into conflicts. Conflicting content under the same ID rejects the entire batch, including new records encountered earlier in that batch.

Events retain the first accepted source URI plus zero-based JSON-array position and their introduction version. New dataset versions preserve previous event provenance. events(version) returns an immutable list; old versions remain available. Readers see a complete state before or after ingestion.

## Current limits and integration status

The store is in memory. Data and retained versions do not survive process restart yet. Runtime dataset persistence remains later work. The default no-argument store ID is local; callers can supply an explicit ID.

This chunk implements importer/store behavior, not the complete user-facing F02 workflow. AgentController.importLogs, active-case dataset references, audit integration and marking an existing assessment stale after a successful import remain to be connected. Query tools remain the next step. The 10 MiB/100,000 defaults are implementation choices, not numbers specified by Stage 1.

Parsing uses [Jackson Core](https://github.com/FasterXML/jackson-core), pinned in pom.xml. The dependency is bundled in the executable JAR so downstream use does not require a hand-built classpath.
