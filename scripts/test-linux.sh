#!/usr/bin/env bash
# Native Linux verification, including real local Qwen inference; no GUI/display required.
set -euo pipefail
script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd -- "$script_dir/.."
if [[ "$(uname -s)" != Linux ]]; then
  printf '%s\n' 'This verification must run on native Linux.' >&2
  exit 1
fi
printf '%s\n' '=== Linux environment ==='
uname -a
free -h
df -h .
python3 --version
java -version
printf '%s\n' '=== Setup helper tests ==='
python3 -m unittest discover -s scripts -p test_model_setup.py
printf '%s\n' '=== Qwen setup, Maven tests, real Sentinel completion and planning ==='
bash scripts/setup-qwen.sh "$@"
printf '%s\n' '=== Real synthetic CLI investigation, cited follow-up, export and reopen ==='
python3 demos/cli-scenario.py
printf '%s\n' '=== Native Linux verification passed ==='
