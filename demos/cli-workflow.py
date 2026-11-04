"""Synthetic packaged-CLI walkthrough; no model service is required."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
jar = root / "target/sentinel-0.1.0-SNAPSHOT.jar"
fixture = root / "data/samples/import-valid.json"
parent = root / "runtime/cli-workflow"
parent.mkdir(parents=True, exist_ok=True)
workspace = Path(tempfile.mkdtemp(prefix="take-", dir=parent))
java = str(Path(os.environ["JAVA_HOME"]) / "bin/java") if os.environ.get("JAVA_HOME") else shutil.which("java")
if not java or not jar.is_file():
    raise SystemExit("Build the packaged JAR first and configure JDK 17 or JAVA_HOME.")
environment = {k: v for k, v in os.environ.items() if not k.startswith("SENTINEL_")}

def invoke(*args, expected=0):
    result = subprocess.run([java, "-jar", str(jar), *args], cwd=workspace,
                            env=environment, capture_output=True, text=True, timeout=30)
    if result.returncode != expected:
        raise RuntimeError(f"CLI {args[0]}: expected {expected}, got {result.returncode}: {result.stderr}")
    if expected:
        return result
    envelope = json.loads(result.stdout)
    assert envelope["status"] == "SUCCESS", envelope
    return envelope["message"]

created = json.loads(invoke("new", "--dataset", "demo", "--request", "Investigate synthetic demo analyst",
                            "--target", "demo-analyst", "--start", "2026-10-22T12:00:00Z",
                            "--end", "2026-10-22T13:00:00Z", "--rule"))
case_id = created["id"]
assert created["status"] == "READY"
summary = json.loads(invoke("import", "--case", case_id, "--file", str(fixture)))
assert summary["datasetVersion"] == "v1"
args = json.dumps(dict(datasetId="demo", datasetVersion="v1", accountId="demo-analyst",
                       start="2026-10-22T12:00:00Z", end="2026-10-22T13:00:00Z", limit="1"))
query = json.loads(invoke("query", "--case", case_id, "--tool", "logSearch", "--args", args))
assert query["status"] == "PARTIAL" and len(query["evidence"]) == 1 and query["truncated"]
invoke("rule", "--case", case_id)
shown = json.loads(invoke("show", "--case", case_id))
assert shown["status"] == "READY" and len(shown["evidence"]) == 1
assert "export is partial" in invoke("preview", "--case", case_id)
report = workspace / "report.md"
invoke("export", "--case", case_id, "--out", str(report))
before = report.read_bytes()
invoke("export", "--case", case_id, "--out", str(report), expected=3)
assert report.read_bytes() == before
reopened = json.loads(invoke("open", "--case", case_id))
assert reopened == shown
assert case_id in invoke("list")
print("Packaged CLI passed: create/import/capped query/rule/show/preview/export/open/list.")
print("Existing export preserved; no model inference requested.")
print(f"Synthetic output directory: {workspace}")
