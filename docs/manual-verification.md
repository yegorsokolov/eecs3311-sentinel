# Manual verification

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
