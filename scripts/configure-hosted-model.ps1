$ErrorActionPreference = 'Stop'
$python = Get-Command python -ErrorAction SilentlyContinue
if (-not $python) { throw 'Install Python 3.10+ and add it to PATH, then rerun.' }
& $python.Source (Join-Path $PSScriptRoot 'model_setup.py') hosted @args
if ($LASTEXITCODE -ne 0) { throw 'Hosted model configuration failed.' }
