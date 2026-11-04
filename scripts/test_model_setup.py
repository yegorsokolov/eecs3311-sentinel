"""Setup behavior tests with fake downloads/processes; never install or contact a model provider."""
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch, Mock
import zipfile

spec = importlib.util.spec_from_file_location('model_setup', Path(__file__).with_name('model_setup.py'))
setup = importlib.util.module_from_spec(spec)
spec.loader.exec_module(setup)


class SetupTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
    def tearDown(self):
        self.temp.cleanup()
    def write(self, updates, replace=False):
        # ACLs are exercised by the real Windows run; avoid OS-specific commands in unit fixtures.
        with patch.object(setup.subprocess, 'check_output', return_value='test-user'), patch.object(setup.subprocess, 'run'):
            setup.write_env(self.root / '.env', updates, replace)
    def test_preserves_unrelated_entries_and_is_idempotent(self):
        env = self.root / '.env'
        env.write_text('# preserved\nSENTINEL_CASE_DIR=cases\n', encoding='utf-8')
        self.write({'SENTINEL_LLM_MODEL': 'qwen2.5:7b'})
        first = env.read_bytes()
        self.write({'SENTINEL_LLM_MODEL': 'qwen2.5:7b'})
        self.assertEqual(first, env.read_bytes())
        self.assertEqual('cases', setup.read_env(env)['SENTINEL_CASE_DIR'])
    def test_refuses_accidental_provider_or_key_replacement(self):
        self.write({'SENTINEL_LLM_API_KEY': 'fixture-secret'})
        before = (self.root / '.env').read_bytes()
        with self.assertRaises(ValueError):
            self.write({'SENTINEL_LLM_API_KEY': ''})
        self.assertEqual(before, (self.root / '.env').read_bytes())
        self.write({'SENTINEL_LLM_API_KEY': ''}, True)
        self.assertEqual('', setup.read_env(self.root / '.env')['SENTINEL_LLM_API_KEY'])
    def test_does_not_execute_dotenv_and_handles_equals_and_quotes(self):
        value = "'fixture=$danger#value"
        self.write({'SENTINEL_LLM_API_KEY': value})
        self.assertEqual(value, setup.read_env(self.root / '.env')['SENTINEL_LLM_API_KEY'])
    def test_invalid_dotenv_and_newline_values_do_not_write(self):
        with self.assertRaises(ValueError):
            self.write({'SENTINEL_LLM_API_KEY': 'fixture\ninjection'})
        env = self.root / '.env'
        env.write_text('BAD LINE', encoding='utf-8')
        with self.assertRaises(ValueError):
            setup.read_env(env)
    def test_hosted_key_prompt_is_not_sent_to_network(self):
        args = Mock(base_url='https://example.com/v1', model='qwen', no_json_mode=False,
                    token_field='max_tokens', replace_config=False)
        with patch.object(setup, 'ROOT', self.root), patch.dict(setup.os.environ, {}, clear=True), \
                patch.object(setup.getpass, 'getpass', return_value='fixture-secret'), \
                patch.object(setup, 'write_env') as writer, patch.object(setup, 'api') as api:
            setup.hosted(args)
            self.assertEqual('fixture-secret', writer.call_args.args[1]['SENTINEL_LLM_API_KEY'])
            api.assert_not_called()
    def test_hosted_rejects_http_or_embedded_credentials(self):
        for url in ['http://example.com/v1', 'https://user:secret@example.com/v1', 'https://example.com?key=secret']:
            with self.assertRaises(ValueError):
                setup.hosted(Mock(base_url=url))
    def test_existing_runtime_does_not_download(self):
        directory = self.root / 'runtime'
        exe = directory / ('ollama.exe' if setup.os.name == 'nt' else 'bin/ollama')
        exe.parent.mkdir(parents=True)
        exe.write_bytes(b'fixture')
        with patch.object(setup.urllib.request, 'urlopen') as network:
            self.assertEqual(exe, setup.install_runtime('0.40.13', directory))
            network.assert_not_called()
    def test_linux_download_verifies_checksum_and_uses_tar(self):
        data = b'fake archive'
        asset = {'name': 'ollama-linux-amd64.tar.zst', 'size': len(data),
                 'digest': 'sha256:' + hashlib.sha256(data).hexdigest(),
                 'browser_download_url': 'https://example.com/fixture'}
        directory = self.root / 'linux'
        def extracted(*args, **kwargs):
            (directory / 'bin').mkdir()
            (directory / 'bin/ollama').write_bytes(b'executable')
        with patch.object(setup.os, 'name', 'posix'), patch.object(setup.platform, 'machine', return_value='x86_64'), \
                patch.object(setup.shutil, 'which', return_value='/usr/bin/tool'), \
                patch.object(setup.urllib.request, 'urlopen', side_effect=[io.BytesIO(json.dumps({'assets': [asset]}).encode()), io.BytesIO(data)]), \
                patch.object(setup.subprocess, 'run', side_effect=extracted) as run:
            self.assertEqual(directory / 'bin/ollama', setup.install_runtime('0.40.13', directory))
            self.assertEqual(['tar', '--zstd', '-xf'], run.call_args.args[0][:3])
    def test_checksum_failure_never_extracts(self):
        asset = {'name': 'ollama-linux-amd64.tar.zst', 'size': 3,
                 'digest': 'sha256:' + '0' * 64, 'browser_download_url': 'https://example.com/fixture'}
        with patch.object(setup.os, 'name', 'posix'), patch.object(setup.platform, 'machine', return_value='x86_64'), \
                patch.object(setup.shutil, 'which', return_value='/usr/bin/tool'), \
                patch.object(setup.urllib.request, 'urlopen', side_effect=[io.BytesIO(json.dumps({'assets': [asset]}).encode()), io.BytesIO(b'bad')]), \
                patch.object(setup.subprocess, 'run') as run:
            with self.assertRaises(ValueError): setup.install_runtime('0.40.13', self.root / 'linux')
            run.assert_not_called()
    def test_existing_model_skips_pull_and_requires_real_smoke_before_env_write(self):
        args = Mock(model='qwen2.5:7b', version='0.40.13', replace_config=False)
        responses = [{'version': 'fixture'}, {'models': [{'name': 'qwen2.5:7b'}]},
                     {'done': True, 'response': '{"status":"ok"}'}]
        with patch.object(setup, 'ROOT', self.root), patch.object(setup, 'install_runtime'), \
                patch.object(setup, 'api', side_effect=responses) as api, patch.object(setup, 'write_env') as writer:
            setup.local(args)
            self.assertEqual(['/api/version', '/api/tags', '/api/generate'], [call.args[1] for call in api.call_args_list])
            writer.assert_called_once()
    def test_failed_smoke_does_not_change_configuration(self):
        args = Mock(model='qwen2.5:7b', version='0.40.13', replace_config=False)
        with patch.object(setup, 'ROOT', self.root), patch.object(setup, 'install_runtime'), \
                patch.object(setup, 'api', side_effect=[{}, {'models': [{'name': args.model}]}, {'done': False}]), \
                patch.object(setup, 'write_env') as writer:
            with self.assertRaises(ValueError): setup.local(args)
            writer.assert_not_called()


    def test_conflicting_process_settings_fail_without_exposing_values(self):
        updates = {'SENTINEL_LLM_PROVIDER': 'ollama', 'SENTINEL_LLM_BASE_URL': 'http://127.0.0.1:11434',
                   'SENTINEL_LLM_MODEL': 'qwen2.5:7b', 'SENTINEL_LLM_API_KEY': ''}
        with patch.dict(setup.os.environ, {'SENTINEL_LLM_API_KEY': 'fixture-secret'}):
            with self.assertRaises(setup.SetupError) as error:
                setup.check_process_overrides(updates)
            self.assertIn('SENTINEL_LLM_API_KEY', str(error.exception))
            self.assertNotIn('fixture-secret', str(error.exception))

    def test_existing_server_never_installs_runtime(self):
        args = Mock(model='qwen2.5:7b', replace_config=False)
        with patch.object(setup, 'ROOT', self.root), patch.object(setup, 'install_runtime') as install, \
                patch.object(setup, 'api', side_effect=[{}, {'models': [{'name': args.model}]},
                                                       {'done': True, 'response': '{"status":"ok"}'}]), \
                patch.object(setup, 'write_env'):
            setup.local(args)
            install.assert_not_called()

    def test_sentinel_check_uses_project_directory_and_checks_failures(self):
        with patch.object(setup.subprocess, 'run') as run, patch.object(setup.subprocess, 'Popen') as launch:
            setup.connect_sentinel('java', {'JAVA_HOME': 'fixture'}, launch=True)
            self.assertEqual(3, run.call_count)
            for call in run.call_args_list:
                self.assertEqual(setup.ROOT, call.kwargs['cwd'])
                self.assertTrue(call.kwargs['check'])
            self.assertIn('verify', run.call_args_list[0].args[0])
            self.assertIn('--check-config', run.call_args_list[1].args[0])
            self.assertTrue(run.call_args_list[2].args[0][-1].endswith('RealQwenCheck.java'))
            self.assertEqual('gui', launch.call_args.args[0][-1])

    def test_failed_sentinel_integration_does_not_launch_gui(self):
        failure = setup.subprocess.CalledProcessError(1, 'java')
        with patch.object(setup.subprocess, 'run', side_effect=[None, None, failure]), \
                patch.object(setup.subprocess, 'Popen') as launch:
            with self.assertRaises(setup.subprocess.CalledProcessError):
                setup.connect_sentinel('java', {}, launch=True)
            launch.assert_not_called()

    def test_default_command_checks_jdk_then_sets_up_and_connects(self):
        order = []
        with patch.object(setup.sys, 'argv', ['model_setup.py', 'local']), \
                patch.object(setup, 'java_environment', side_effect=lambda: (order.append('jdk') or ('java', {}))), \
                patch.object(setup, 'local', side_effect=lambda args: order.append('local')), \
                patch.object(setup, 'connect_sentinel', side_effect=lambda *args: order.append('connect')):
            self.assertEqual(0, setup.main())
        self.assertEqual(['jdk', 'local', 'connect'], order)

    def test_model_only_skips_application_and_jdk(self):
        with patch.object(setup.sys, 'argv', ['model_setup.py', 'local', '--model-only']), \
                patch.object(setup, 'java_environment') as jdk, patch.object(setup, 'local') as local, \
                patch.object(setup, 'connect_sentinel') as connect:
            self.assertEqual(0, setup.main())
            local.assert_called_once()
            jdk.assert_not_called()
            connect.assert_not_called()

    def test_new_server_starts_hidden_and_is_probed_before_inference(self):
        args = Mock(model='qwen2.5:7b', version='0.40.13', backend='auto', replace_config=False)
        process = Mock(pid=123, poll=Mock(return_value=None))
        with patch.object(setup, 'ROOT', self.root), \
                patch.object(setup, 'install_runtime', return_value=self.root / 'ollama') as install, \
                patch.object(setup.subprocess, 'Popen', return_value=process) as spawn, \
                patch.object(setup, 'api', side_effect=[OSError(), {}, {'models': [{'name': args.model}]},
                                                       {'done': True, 'response': '{"status":"ok"}'}]), \
                patch.object(setup, 'write_env'):
            setup.local(args)
            install.assert_called_once()
            self.assertEqual('serve', spawn.call_args.args[0][-1])
            self.assertEqual('127.0.0.1:11434', spawn.call_args.kwargs['env']['OLLAMA_HOST'])
            self.assertEqual(setup.subprocess.CREATE_NO_WINDOW if setup.os.name == 'nt' else 0,
                             spawn.call_args.kwargs['creationflags'])
            self.assertEqual('123', (self.root / 'runtime/ollama/setup-server.pid').read_text())


if __name__ == '__main__':
    unittest.main()
