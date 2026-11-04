#!/usr/bin/env python3
"""Shared Windows/Linux setup. Uses only Python's standard library; never executes .env."""
import argparse
import getpass
import hashlib
import json
import os
from pathlib import Path
import platform
import re
import shutil
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parent.parent
MODEL_PATTERN = r"[A-Za-z0-9][A-Za-z0-9._:/-]*"
class SetupError(ValueError):
    """Safe, application-owned diagnostic text; never wraps a provider response."""



def read_env(path):
    result = {}
    if path.exists():
        for raw in path.read_text(encoding="utf-8-sig").splitlines():
            line = raw.strip()
            if not line or line.startswith("#"):
                continue
            key, sep, value = line.partition("=")
            if not sep or not re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", key.strip()):
                raise SetupError("Malformed .env; correct it before setup (contents withheld).")
            value = value.strip()
            if value.startswith(('"', "'")):
                if len(value) < 2 or value[-1] != value[0]:
                    raise SetupError("Unmatched .env quote (contents withheld).")
                value = value[1:-1]
            result[key.strip()] = value
    return result


def write_env(path, updates, replace=False):
    existing = read_env(path)
    changes = [key for key, value in updates.items()
               if existing.get(key) and existing[key] != value]
    if changes and not replace:
        raise SetupError("Existing model configuration differs. Use --replace-config to switch deliberately; unrelated entries are preserved.")
    for value in updates.values():
        if '\n' in value or '\r' in value:
            raise SetupError("Configuration values must be single-line.")
    lines = path.read_text(encoding="utf-8-sig").splitlines() if path.exists() else []
    lines = [line for line in lines if line.partition('=')[0].strip() not in updates]
    # Quotes protect keys whose first character is a quote; dotenv does not perform interpolation.
    lines += [key + '=' + ('"' + value + '"' if value else '') for key, value in updates.items()]
    fd, temp = tempfile.mkstemp(prefix=".env-", dir=path.parent)
    try:
        os.chmod(temp, 0o600)
        if os.name == 'nt':
            identity = subprocess.check_output(['whoami'], text=True).strip()
            subprocess.run(['icacls', temp, '/inheritance:r', '/grant:r', identity + ':(F)'],
                           check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        with os.fdopen(fd, 'w', encoding='utf-8', newline='\n') as stream:
            fd = None
            stream.write('\n'.join(lines).rstrip() + '\n')
        os.replace(temp, path)
    finally:
        if fd is not None:
            os.close(fd)
        if os.path.exists(temp):
            os.unlink(temp)


def api(base, route, payload=None, timeout=5):
    data = None if payload is None else json.dumps(payload).encode()
    request = urllib.request.Request(base + route, data=data, headers={'Content-Type': 'application/json'})
    with urllib.request.urlopen(request, timeout=timeout) as response:
        return json.load(response)


def install_runtime(version, directory):
    windows = os.name == 'nt'
    machine = platform.machine().lower()
    arch = 'amd64' if machine in ('amd64', 'x86_64') else 'arm64' if machine in ('aarch64', 'arm64') else None
    if arch is None or (windows and arch != 'amd64'):
        raise SetupError('Supported: Windows x64 and Linux x64/ARM64.')
    executable = directory / ('ollama.exe' if windows else 'bin/ollama')
    if executable.is_file():
        return executable
    if not windows and (not shutil.which('tar') or not shutil.which('zstd')):
        raise SetupError('Install tar and zstd with your Linux package manager, then rerun.')
    name = 'ollama-windows-amd64.zip' if windows else f'ollama-linux-{arch}.tar.zst'
    release_url = f'https://api.github.com/repos/ollama/ollama/releases/tags/v{version}'
    with urllib.request.urlopen(urllib.request.Request(release_url, headers={'User-Agent': 'Sentinel-setup'}), timeout=30) as response:
        release = json.load(response)
    asset = next((item for item in release['assets'] if item['name'] == name), None)
    if not asset or not (asset.get('digest') or '').startswith('sha256:'):
        raise SetupError('Official release lacks the expected package/checksum; no binary was installed.')
    directory.mkdir(parents=True, exist_ok=True)
    archive = directory.parent / (name + '.download')
    print(f'Downloading official Ollama {version}: {asset["size"] / 1e9:.2f} GB', flush=True)
    digest = hashlib.sha256()
    with urllib.request.urlopen(asset['browser_download_url'], timeout=120) as source, archive.open('wb') as destination:
        while True:
            chunk = source.read(1024 * 1024)
            if not chunk:
                break
            destination.write(chunk)
            digest.update(chunk)
    if digest.hexdigest() != asset['digest'].split(':', 1)[1]:
        raise SetupError('Ollama download checksum mismatch; installation stopped.')
    if windows:
        with zipfile.ZipFile(archive) as package:
            for member in package.infolist():
                target = (directory / member.filename).resolve()
                if not target.is_relative_to(directory.resolve()):
                    raise SetupError('Unsafe archive entry.')
            package.extractall(directory)
    else:
        subprocess.run(['tar', '--zstd', '-xf', str(archive), '-C', str(directory)], check=True)
    if not executable.is_file():
        raise SetupError('Runtime archive did not contain the expected executable.')
    archive.unlink()
    return executable


def local(args):
    base = 'http://127.0.0.1:11434'
    env_path = ROOT / '.env'
    updates = dict(SENTINEL_LLM_PROVIDER='ollama', SENTINEL_LLM_BASE_URL=base,
                   SENTINEL_LLM_MODEL=args.model, SENTINEL_LLM_API_KEY='',
                   SENTINEL_LLM_JSON_MODE='true', SENTINEL_LLM_TOKEN_FIELD='max_tokens')
    check_process_overrides(updates)
    existing = read_env(env_path)
    if any(existing.get(k) and existing[k] != v for k, v in updates.items()) and not args.replace_config:
        raise SetupError('Existing model settings differ. Use --replace-config to switch to local Qwen.')
    runtime = ROOT / 'runtime/ollama'
    runtime.mkdir(parents=True, exist_ok=True)
    directory = runtime / ('bin' if os.name == 'nt' else 'linux')
    try:
        api(base, '/api/version')
        print('Reusing Ollama on loopback; its existing storage/backend settings remain in effect.', flush=True)
    except (OSError, ValueError):
        executable = install_runtime(args.version, directory)
        env = os.environ.copy()
        env.update(OLLAMA_HOST='127.0.0.1:11434', OLLAMA_MODELS=str(runtime / 'models'),
                   OLLAMA_NO_CLOUD='1', OLLAMA_NOPRUNE='1')
        if args.backend != 'auto':
            env['OLLAMA_LLM_LIBRARY'] = args.backend
        else:
            env.pop('OLLAMA_LLM_LIBRARY', None)
        if os.name == 'nt':
            env['OLLAMA_VULKAN'] = 'false'
        flags = subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0
        with (runtime / 'setup-server.log').open('ab') as log:
            process = subprocess.Popen([str(executable), 'serve'], env=env, stdin=subprocess.DEVNULL,
                                       stdout=log, stderr=log, creationflags=flags,
                                       start_new_session=os.name != 'nt')
        (runtime / 'setup-server.pid').write_text(str(process.pid), encoding='ascii')
        deadline = time.monotonic() + 120
        while True:
            if process.poll() is not None:
                raise SetupError('Ollama exited; inspect runtime/ollama/setup-server.log.')
            try:
                api(base, '/api/version', timeout=2)
                break
            except (OSError, ValueError):
                if time.monotonic() >= deadline:
                    process.terminate()
                    raise SetupError('Ollama startup timed out; inspect setup-server.log.')
                time.sleep(1)
    tags = api(base, '/api/tags')
    if args.model not in {item['name'] for item in tags.get('models', [])}:
        print(f'Pulling {args.model}; the default 7B model is about 4.7 GB.', flush=True)
        # Stream progress without logging arbitrary provider bodies.
        request = urllib.request.Request(base + '/api/pull', data=json.dumps({'model': args.model}).encode(),
                                         headers={'Content-Type': 'application/json'})
        last = -1
        with urllib.request.urlopen(request, timeout=1800) as response:
            for line in response:
                item = json.loads(line)
                if 'error' in item:
                    raise SetupError('Model pull failed; inspect the Ollama server log.')
                if item.get('total'):
                    progress = int(100 * item.get('completed', 0) / item['total']) // 10 * 10
                    if progress != last:
                        print(f'Download: {progress}%', flush=True)
                        last = progress
    print('Checking real local inference (cold preload allows 300 seconds)...', flush=True)
    result = api(base, '/api/generate', {'model': args.model, 'stream': False, 'format': 'json',
                  'prompt': 'Return exactly one JSON object with status equal to ok.',
                  'keep_alive': '30m', 'options': {'temperature': 0, 'num_predict': 32}}, timeout=300)
    if not result.get('done') or json.loads(result.get('response', '{}')).get('status') != 'ok':
        raise SetupError('The real model smoke check failed.')
    write_env(env_path, updates, args.replace_config)
    print('Local Qwen ready; .env configured. No API key is needed. No paid API was called.')


def check_process_overrides(updates):
    expected = dict(updates, SENTINEL_OLLAMA_URL=updates['SENTINEL_LLM_BASE_URL'],
                    SENTINEL_OLLAMA_MODEL=updates['SENTINEL_LLM_MODEL'])
    conflicts = [key for key, value in expected.items()
                 if os.environ.get(key, '').strip() and os.environ[key].strip() != value]
    if conflicts:
        raise SetupError('Process environment overrides local setup. Clear these variables and rerun: '
                         + ', '.join(sorted(conflicts)))


def java_environment():
    env = os.environ.copy()
    home = env.get('JAVA_HOME')
    java = str(Path(home) / 'bin' / ('java.exe' if os.name == 'nt' else 'java')) if home else shutil.which('java')
    if not java:
        raise SetupError('Install JDK 17+ and set JAVA_HOME or add its bin directory to PATH.')
    result = subprocess.run([java, '-version'], capture_output=True, text=True, timeout=15)
    match = re.search(r'version "(\d+)', result.stderr + result.stdout)
    if result.returncode or not match or int(match[1]) < 17:
        raise SetupError('JDK 17+ is required; check JAVA_HOME and PATH.')
    if not home:
        # Maven Wrapper also needs JAVA_HOME, including when java is on PATH.
        settings = subprocess.run([java, '-XshowSettings:properties', '-version'],
                                  capture_output=True, text=True, check=True, timeout=15)
        match = re.search(r'^\s*java.home = (.+)$', settings.stderr + settings.stdout, re.MULTILINE)
        if not match:
            raise SetupError('Cannot locate JDK; set JAVA_HOME and rerun.')
        env['JAVA_HOME'] = match[1].strip()
    if not (Path(env['JAVA_HOME']) / 'bin' / ('javac.exe' if os.name == 'nt' else 'javac')).is_file():
        raise SetupError('A full JDK 17+ is required, including javac; check JAVA_HOME.')
    return java, env


def connect_sentinel(java, env, launch=False):
    print('Building Sentinel and running its automated tests...', flush=True)
    wrapper = [str(ROOT / 'mvnw.cmd')] if os.name == 'nt' else ['sh', str(ROOT / 'mvnw')]
    subprocess.run(wrapper + ['--batch-mode', 'verify'], cwd=ROOT, env=env, check=True)
    jar = ROOT / 'target/sentinel-0.1.0-SNAPSHOT.jar'
    subprocess.run([java, '-jar', str(jar), '--check-config'], cwd=ROOT, env=env, check=True)
    print('Checking Qwen through Sentinel configuration, adapter and validated planner...', flush=True)
    subprocess.run([java, '--class-path', str(jar), str(ROOT / 'demos/RealQwenCheck.java')],
                   cwd=ROOT, env=env, check=True)
    print('Sentinel connected to local Qwen: real completion and validated model plan passed.', flush=True)
    if launch:
        subprocess.Popen([java, '-jar', str(jar), 'gui'], cwd=ROOT, env=env)
        print('Sentinel GUI launched.', flush=True)


def hosted(args):
    from urllib.parse import urlsplit
    endpoint = urlsplit(args.base_url)
    if endpoint.scheme != 'https' or not endpoint.hostname or endpoint.username or endpoint.password or endpoint.query or endpoint.fragment:
        raise SetupError('Hosted base URL must use HTTPS with no credentials, query or fragment.')
    key = os.environ.get('SENTINEL_LLM_API_KEY') or getpass.getpass('Provider API key (hidden; stored only in ignored .env): ')
    if not key or len(key) > 8192 or any(ord(c) < 33 or ord(c) > 126 for c in key):
        raise SetupError('API key must be a nonempty printable ASCII token.')
    write_env(ROOT / '.env', dict(SENTINEL_LLM_PROVIDER='openai-compatible',
              SENTINEL_LLM_BASE_URL=args.base_url.rstrip('/'), SENTINEL_LLM_MODEL=args.model,
              SENTINEL_LLM_API_KEY=key, SENTINEL_LLM_JSON_MODE=str(not args.no_json_mode).lower(),
              SENTINEL_LLM_TOKEN_FIELD=args.token_field), args.replace_config)
    print('Hosted configuration saved with restricted file permissions. No paid request was sent.')


def main():
    if sys.version_info < (3, 10):
        print('Python 3.10 or newer is required.', file=sys.stderr)
        return 1
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='command', required=True)
    setup = commands.add_parser('local', help='Install/start Ollama, pull Qwen, test inference, configure Sentinel')
    setup.add_argument('--model', default='qwen2.5:7b')
    setup.add_argument('--version', default='0.40.13')
    setup.add_argument('--backend', default='cuda_v12' if os.name == 'nt' else 'auto', choices=['auto', 'cuda_v12', 'cuda_v13'])
    setup.add_argument('--replace-config', action='store_true')
    setup.add_argument('--launch', action='store_true', help='Open Sentinel GUI after successful setup and integration checks')
    setup.add_argument('--model-only', action='store_true', help='Only prepare Ollama/Qwen; skip JDK, build and Sentinel integration checks')
    cloud = commands.add_parser('hosted', help='Configure a user-supplied OpenAI-compatible provider')
    cloud.add_argument('--base-url', required=True, help='API base URL including version path, without /chat/completions')
    cloud.add_argument('--model', required=True)
    cloud.add_argument('--no-json-mode', action='store_true')
    cloud.add_argument('--token-field', default='max_tokens', choices=['max_tokens', 'max_completion_tokens'])
    cloud.add_argument('--replace-config', action='store_true')
    args = parser.parse_args()
    if not re.fullmatch(MODEL_PATTERN, args.model):
        parser.error('Invalid model identifier.')
    if args.command == 'local' and not re.fullmatch(r'\d+\.\d+\.\d+', args.version):
        parser.error('Version must have three numeric components.')
    try:
        if args.command == 'local':
            if args.model_only and args.launch:
                raise SetupError('--launch requires the Sentinel checks; omit --model-only.')
            java, env = java_environment() if not args.model_only else (None, None)
            local(args)
            if not args.model_only:
                connect_sentinel(java, env, args.launch)
        else:
            hosted(args)
    except SetupError as error:
        print('Setup failed: ' + str(error), file=sys.stderr)
        return 1
    except (OSError, ValueError, subprocess.SubprocessError, KeyError, StopIteration):
        # Exception strings can contain URLs, provider bodies or secrets: keep them out of console logs.
        print('Setup failed. Check prerequisites, existing .env settings (use --replace-config to switch), network access and runtime/ollama/setup-server.log. No success is claimed.', file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
