# Start the portable runtime downloaded for local verification. Run from any directory.
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$ollamaExe = Join-Path $projectRoot 'runtime/ollama/bin/ollama.exe'
if (-not (Test-Path -LiteralPath $ollamaExe)) { throw 'Portable Ollama is missing. Install Ollama or download its official Windows standalone package into runtime/ollama/bin.' }
$env:OLLAMA_MODELS = Join-Path $projectRoot 'runtime/ollama/models'
$env:OLLAMA_HOST = '127.0.0.1:11434'
$env:OLLAMA_VULKAN = 'false'
$env:OLLAMA_NO_CLOUD = '1'
$env:OLLAMA_LLM_LIBRARY = 'cuda_v12'
# Avoid automatic manifest pruning, which fails in this OneDrive workspace.
$env:OLLAMA_NOPRUNE = '1'
& $ollamaExe serve
