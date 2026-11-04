#!/usr/bin/env python3
"""Real-model CLI smoke check with isolated synthetic data; run after Qwen setup/build."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import time
import uuid

ROOT = Path(__file__).resolve().parent.parent


def main():
    run_id = 'smoke-' + uuid.uuid4().hex[:10]
    output = ROOT / 'runtime/test-runs' / run_id
    output.mkdir(parents=True)
    env = os.environ.copy()
    env.update(SENTINEL_DATA_DIR=str(output / 'datasets'), SENTINEL_CASE_DIR=str(output / 'cases'),
               SENTINEL_EXPORT_DIR=str(output / 'exports'))
    java = str(Path(env['JAVA_HOME']) / 'bin' / ('java.exe' if os.name == 'nt' else 'java')) \
        if env.get('JAVA_HOME') else shutil.which('java')
    if not java:
        raise RuntimeError('JDK 17+ is required.')
    records = []

    def invoke(*args):
        started = time.monotonic()
        process = subprocess.run([java, '-jar', str(ROOT / 'target/sentinel-0.1.0-SNAPSHOT.jar'), *args],
                                 cwd=ROOT, env=env, capture_output=True, text=True, encoding='utf-8', timeout=420)
        records.append({'command': args[0], 'exitCode': process.returncode,
                        'seconds': round(time.monotonic() - started, 2),
                        'stdout': process.stdout, 'stderr': process.stderr})
        (output / 'commands.json').write_text(json.dumps(records, indent=2), encoding='utf-8')
        print(args[0], 'exit=' + str(process.returncode), 'seconds=' + str(records[-1]['seconds']), flush=True)
        if process.returncode:
            raise RuntimeError('CLI command failed: ' + args[0] + '; see ' + str(output / 'commands.json'))
        result = json.loads(process.stdout)
        if result['status'] != 'SUCCESS':
            raise RuntimeError('CLI returned a non-success result: ' + args[0])
        return json.loads(result['message'])

    case = invoke('new', '--dataset', run_id, '--request', 'Investigate synthetic account takeover',
                  '--target', 'demo-analyst', '--start', '2026-10-22T12:00:00Z',
                  '--end', '2026-10-22T13:00:00Z', '--feed', 'demo-feed-v1', '--rule')
    case_id = case['id']
    invoke('import', '--case', case_id, '--file', 'data/scenarios/account-takeover.json')
    invoke('rule', '--case', case_id)
    assessment = invoke('run', '--case', case_id)
    if assessment['severity']['score'] != 7 or assessment['severity']['level'] != 'HIGH':
        raise RuntimeError('Unexpected deterministic synthetic-case severity.')
    answer = invoke('ask', '--case', case_id, '--question', 'What evidence supports this assessment?')
    report = output / 'report.md'
    invoke('export', '--case', case_id, '--out', str(report))
    reopened = invoke('open', '--case', case_id)
    if reopened['id'] != case_id or not report.is_file():
        raise RuntimeError('Case persistence or report export failed.')
    if assessment['completeness'] != 'COMPLETE' or not assessment['hypotheses'] or not answer['evidenceIds']:
        raise RuntimeError('Real model assessment/follow-up incomplete; inspect ' + str(output / 'commands.json'))
    print('REAL CLI PASS: COMPLETE / 7 HIGH, cited follow-up, exported report and reopened case.', flush=True)
    print('Evidence:', output, flush=True)


if __name__ == '__main__':
    main()
