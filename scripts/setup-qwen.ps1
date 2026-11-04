# Requires Python 3.10+. Arguments are passed as separate values, never evaluated.
$ErrorActionPreference = 'Stop'
$python = Get-Command python -ErrorAction SilentlyContinue
if (-not $python) { throw 'Install Python 3.10+ and add it to PATH, then rerun.' }
& $python.Source (Join-Path $PSScriptRoot 'model_setup.py') local @args
if ($LASTEXITCODE -ne 0) { throw 'Qwen setup failed. See the diagnostic above.' }
