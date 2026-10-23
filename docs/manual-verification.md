# Manual verification

## Step 4 - validated dataset import

In PowerShell at the project root:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
.\mvnw.cmd --batch-mode --no-transfer-progress "-Dtest=LogImporterTest" test
.\mvnw.cmd --batch-mode --no-transfer-progress verify
& "$env:JAVA_HOME\bin\jshell.exe" --class-path target/sentinel-0.1.0-SNAPSHOT.jar demos/import-demo.jsh
```

Expected: 31 import tests, then 96 total tests, all passing. Maven packages the runtime JSON parser into the JAR. A manifest-overlap warning from shading is benign; the packaged JAR was checked directly.

The demo prints:

1. Valid import: version v1, accepted 2, duplicates 1, no errors.
2. First timestamp: 2026-10-22T12:00:00Z.
3. Source URI ending in import-valid.json#/0.
4. Reimport: version v1, accepted 0, duplicates 3.
5. Conflict: rejected with accepted 0 and version v1.
6. Missing timestamp offset: rejected with accepted 0 and version v1.
7. Final version/count: v1/2.

The conflict file places a new event before the conflicting event, so the final count proves that no earlier part of that batch leaked into the store. The demo reads source files unchanged and stores data only in memory. It does not need Ollama, create a case, or save anything to disk.

For interactive inspection, start JShell with the same JAR classpath (omit the script argument), then enter:

```java
import sentinel.data.*;
import java.nio.file.Path;
var store = new DatasetStore("review");
var importer = new LogImporter(store);
importer.importFile(Path.of("data/samples/import-valid.json"));
var savedView = store.events("v1");
savedView.clear();
importer.importFile(Path.of("data/samples/import-conflict.json"));
store.currentVersion();
savedView.size();
/exit
```

Expected: clearing savedView throws UnsupportedOperationException, the conflict returns errors, currentVersion remains v1, and savedView.size() is 2. Record your review before requesting a commit.

Videos are deferred until all features work. Perform the relevant checks after each implementation step.

## Step 1 - build and configuration

Open PowerShell and run:

```powershell
Set-Location 'C:\Users\yegor\OneDrive\Documents\YorkU\EECS 3311\Project\eecs3311-sentinel'
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
.\mvnw.cmd --batch-mode verify
$LASTEXITCODE
```

Expected: BUILD SUCCESS, no failing tests, exit code 0, and a JAR under target/. First use downloads Maven and dependencies.

The initial agent run could not create target/classes in this OneDrive folder. A later retry succeeded in place, and Step 2 also compiled and tested in place. No permission changes were needed. If this output-directory error recurs, the following non-destructive alternate build is available:

```powershell
$sentinelCheckRoot = Join-Path $env:TEMP ('sentinel-check-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $sentinelCheckRoot | Out-Null
Copy-Item -LiteralPath pom.xml -Destination $sentinelCheckRoot
Copy-Item -LiteralPath src -Destination $sentinelCheckRoot -Recurse
.\mvnw.cmd --batch-mode -f "$sentinelCheckRoot\pom.xml" verify
$sentinelJar = Join-Path $sentinelCheckRoot 'target\sentinel-0.1.0-SNAPSHOT.jar'
& "$env:JAVA_HOME\bin\java.exe" -jar $sentinelJar --check-config
```

Keep the terminal working directory at the original project root: this makes diagnostics read the original .env and resolve storage paths there. Substitute `$sentinelJar` for `target/sentinel-0.1.0-SNAPSHOT.jar` in the remaining commands. This alternate copy is for verification only; edit source in the original repository.

Check defaults (assuming no SENTINEL_* process overrides):

```powershell
& "$env:JAVA_HOME\bin\java.exe" -jar target/sentinel-0.1.0-SNAPSHOT.jar --check-config
$LASTEXITCODE
(Get-Item -Force .env).Length
```

Expected: Configuration valid; localhost:11434; qwen2.5:7b; runtime storage paths; limits 8/2/6/10/60/1; exit 0; .env length 0. This does not verify Ollama availability.

Check a temporary override, then invalid input, restoring any pre-existing setting:

```powershell
$sentinelPreviousUrl = $env:SENTINEL_OLLAMA_URL
try {
    $env:SENTINEL_OLLAMA_URL = 'http://localhost:11435'
    & "$env:JAVA_HOME\bin\java.exe" -jar target/sentinel-0.1.0-SNAPSHOT.jar --check-config
    $LASTEXITCODE # Expected 0 and the changed URL
    $env:SENTINEL_OLLAMA_URL = 'not-a-url'
    & "$env:JAVA_HOME\bin\java.exe" -jar target/sentinel-0.1.0-SNAPSHOT.jar --check-config
    $LASTEXITCODE # Expected 2 and a concise configuration error
} finally {
    $env:SENTINEL_OLLAMA_URL = $sentinelPreviousUrl
}
```

Check interface status:

```powershell
& "$env:JAVA_HOME\bin\java.exe" -jar target/sentinel-0.1.0-SNAPSHOT.jar --help
& "$env:JAVA_HOME\bin\java.exe" -jar target/sentinel-0.1.0-SNAPSHOT.jar --gui
$LASTEXITCODE
```

Expected: help explains the diagnostic; --gui returns unsupported-command error and exit 2. GUI implementation is later.

The automated tests cover .env file parsing in temporary directories, so manual checks do not require changing your empty local .env. Record your review outcome in the collaboration log after completing these checks.

## Step 2 - core domain contracts

In PowerShell at the project root:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
.\mvnw.cmd --batch-mode --no-transfer-progress "-Dtest=CoreDomainTest" test
.\mvnw.cmd --batch-mode --no-transfer-progress verify
```

Expected: 23 domain tests pass; full verification passes 40 tests with zero failures/errors/skips and packages the JAR. No Ollama service is required.

Optional interactive review: start JShell from PowerShell:

```powershell
& "$env:JAVA_HOME\bin\jshell.exe" --class-path target/classes
```

Then enter these Java expressions at the JShell prompt, one line at a time:

```java
import sentinel.domain.*;
import java.time.Instant;
import java.util.*;
var start = Instant.parse("2026-10-20T12:00:00Z");
var end = Instant.parse("2026-10-20T13:00:00Z");
var scope = new CaseScope("demo", "v1", "alice", start, end);
new Request("Investigate alice", scope);
new Request("Investigate suspicious logins", null);
new CaseScope("demo", "v1", "alice", end, start);
new Request("   ", scope);
var event = new Event("e1", start, "login", null, null, null, null, "demo.json#/0", "v1");
var source = new ArrayList<Event>(List.of(event));
var batch = new ImportBatch(source);
source.clear();
batch.events().size();
batch.events().clear();
/exit
```

Expected, in order:

- Valid scope and scoped request are constructed.
- The unscoped request is accepted for later clarification; this does not create a case or permit execution.
- The reversed interval throws IllegalArgumentException: end must not precede start.
- The blank goal throws IllegalArgumentException.
- The event retains null account/IP/host/outcome values and explicit source provenance.
- Clearing the original source list leaves batch.events().size() equal to 1.
- Attempting to clear the batch list throws UnsupportedOperationException.

Record review outcomes before requesting a commit. Case lifecycle, clarification interactions, duplicate handling and import execution are later chunks. Other record invariants will be implemented with their owning features, rather than guessing their contracts now.

## Step 3 - case lifecycle and snapshots

Run from the project root in PowerShell:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
.\mvnw.cmd --batch-mode --no-transfer-progress "-Dtest=InvestigationTest" test
.\mvnw.cmd --batch-mode --no-transfer-progress verify
```

Expected: 25 lifecycle tests, then 65 total tests; both commands finish with BUILD SUCCESS and zero failures/errors. Tests cover lifecycle guards, atomic rejection, immutable nested snapshots, restoration, reference integrity and preserved failure data. No Ollama service is required.

Start an interactive review:

```powershell
& "$env:JAVA_HOME\bin\jshell.exe" --class-path target/classes
```

Enter the following at the JShell prompt:

```java
import sentinel.domain.*;
import java.time.Instant;
import java.util.*;
var start = Instant.parse("2026-10-21T12:00:00Z");
var scope = new CaseScope("demo", "v1", "alice", start, start.plusSeconds(3600));
var investigation = new Investigation(new Request("Investigate alice", scope));
investigation.snapshot().status();
var call = new ToolCall(UUID.randomUUID(), "logSearch", Map.of("accountId", "alice"));
var plan = new Plan(UUID.randomUUID(), 0, List.of(call));
investigation.setPlan(plan);
investigation.snapshot().status();
investigation.record(AuditEntry.transition(Instant.now(), CaseStatus.RUNNING));
var saved = investigation.snapshot();
var reopened = Investigation.restore(saved);
saved.status();
reopened.snapshot().status();
reopened.record(AuditEntry.transition(Instant.now(), CaseStatus.RUNNING));
reopened.setPlan(plan);
reopened.snapshot().status();
saved.history().clear();
reopened.record(AuditEntry.transition(Instant.now(), CaseStatus.RUNNING));
reopened.record(AuditEntry.transition(Instant.now(), CaseStatus.PARTIAL));
reopened.snapshot().status();
saved.status();
/exit
```

Expected flow:

1. A new case is DRAFT. Accepting the sample plan makes it READY without running anything.
2. Explicit Run records RUNNING. Restoring that snapshot produces PAUSED; the saved snapshot stays RUNNING.
3. Attempting to run the paused case directly throws IllegalStateException.
4. Explicit plan acceptance returns it to READY. This is the aggregate boundary; the future planner must validate tools and scope before calling setPlan.
5. Clearing snapshot history throws UnsupportedOperationException.
6. A revalidated case can run and finish PARTIAL. The original snapshot still says RUNNING.

This is an in-memory demonstration, not JSON save/open or real tool execution. Completion requires an accepted COMPLETE assessment; failed runs retain their evidence/history. New evidence ingestion and actual analysis come in later chunks.
