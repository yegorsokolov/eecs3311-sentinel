# Manual verification

Videos are deferred until all features work. Perform these checks after reviewing Step 1.

## Step 1 - build and configuration

Open PowerShell and run:

```powershell
Set-Location 'C:\Users\yegor\OneDrive\Documents\YorkU\EECS 3311\Project\eecs3311-sentinel'
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
.\mvnw.cmd --batch-mode verify
$LASTEXITCODE
```

Expected: BUILD SUCCESS, no failing tests, exit code 0, and a JAR under target/. First use downloads Maven and dependencies.

The agent's run could not create target/classes in this OneDrive folder. The same code passed all 17 tests and packaging in a temporary copy. If your terminal also reports that output-directory error, use this non-destructive alternate build:

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
