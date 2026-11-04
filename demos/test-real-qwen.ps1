# Run after Maven packaging and starting Ollama. A failed check returns a nonzero exit code.
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
Push-Location $projectRoot
try {
    $java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { 'java' }
    & $java --class-path target/sentinel-0.1.0-SNAPSHOT.jar demos/RealQwenCheck.java
    if ($LASTEXITCODE -ne 0) { throw "Real Qwen check failed (exit $LASTEXITCODE)." }
} finally { Pop-Location }
