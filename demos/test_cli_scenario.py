"""Packaged demo replay regressions using a fake native Ollama HTTP service."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import unittest
import uuid

ROOT = Path(__file__).resolve().parents[1]

class ReplayTests(unittest.TestCase):
    def setUp(self):
        self.calls = []
        self.invalid_analysis = False
        owner = self
        class Handler(BaseHTTPRequestHandler):
            def log_message(self, *_):
                pass
            def do_POST(self):
                request = json.loads(self.rfile.read(int(self.headers['Content-Length'])))
                owner.calls.append(request['system'])
                if 'purpose: answer' in request['system']:
                    response = dict(text='Review the cited login.', evidenceIds=['E1'], limitations=['Synthetic fake-server fixture'])
                elif owner.invalid_analysis:
                    response = dict(invalid='fixture')
                else:
                    response = dict(version='analysis-v1', hypothesis=dict(text='Possible suspicious login', ref='E1', against=[], confidence='LOW', limitation='Synthetic fixture'), recommendation=dict(action='REVIEW', reason='Review cited event', ref='E1', priority='HIGH', prerequisite='Analyst approval'))
                body = json.dumps(dict(model='qwen2.5:7b', response=json.dumps(response), done=True)).encode()
                self.send_response(200)
                self.send_header('Content-Type', 'application/json')
                self.send_header('Content-Length', str(len(body)))
                self.end_headers()
                self.wfile.write(body)
        self.server = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.temp = tempfile.TemporaryDirectory(prefix='sentinel-replay-')
        self.environment = {k: v for k, v in os.environ.items() if not k.startswith('SENTINEL_')}
        self.environment['SENTINEL_OLLAMA_URL'] = f'http://127.0.0.1:{self.server.server_port}'
        for name, folder in [('SENTINEL_DATA_DIR', 'datasets'), ('SENTINEL_CASE_DIR', 'cases'), ('SENTINEL_EXPORT_DIR', 'exports')]:
            self.environment[name] = str(Path(self.temp.name) / folder)
    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join(2)
        self.temp.cleanup()
    def invoke(self, command, expected=0, calls=2):
        result = subprocess.run(command, cwd=ROOT, env=self.environment, capture_output=True, text=True, encoding='utf-8', errors='replace', timeout=120)
        self.assertEqual(expected, result.returncode, result.stdout + result.stderr)
        self.assertEqual(calls, len(self.calls), 'Unexpected fake-service request count')
        return result
    def test_portable_replay_checks_complete_score_citations_export_and_reopen(self):
        result = self.invoke([os.sys.executable, str(ROOT / 'demos/cli-scenario.py')])
        # The helper's success label is exercised against a fake server here, not real inference.
        self.assertIn('COMPLETE / 7 HIGH', result.stdout)
        self.assertIn('cited follow-up', result.stdout)
    @unittest.skipUnless(shutil.which('powershell.exe'), 'Windows PowerShell is not installed')
    def test_powershell_array_binding_and_stderr_progress_do_not_abort(self):
        result = self.invoke([shutil.which('powershell.exe'), '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', str(ROOT / 'demos/cli-scenario.ps1'), '-DatasetId', 'replay-' + uuid.uuid4().hex[:10]])
        self.assertIn('COMPLETE / score 7 HIGH', result.stdout)
        self.assertIn('Report:', result.stdout)

    def test_portable_replay_rejects_partial_analysis(self):
        self.invalid_analysis = True
        result = self.invoke([os.sys.executable, str(ROOT / 'demos/cli-scenario.py')], expected=1, calls=3)
        self.assertIn('incomplete', result.stderr.lower())
    @unittest.skipUnless(shutil.which('powershell.exe'), 'Windows PowerShell is not installed')
    def test_powershell_replay_rejects_partial_analysis(self):
        self.invalid_analysis = True
        result = self.invoke([shutil.which('powershell.exe'), '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', str(ROOT / 'demos/cli-scenario.ps1'), '-DatasetId', 'partial-' + uuid.uuid4().hex[:10]], expected=1, calls=2)
        self.assertIn('incomplete', result.stderr.lower())

if __name__ == '__main__':
    unittest.main()
