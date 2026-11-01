# Model provider setup

Sentinel defaults to local Qwen2.5 7B Instruct through Ollama. Local inference needs **no API key** and does not call a paid provider. The model is approximately 4.7 GB on disk (Q4_K_M); the portable runtime needs additional space. Allow at least 12 GB of free disk for download/extraction and model files. On the tested machine, 32 GB RAM and a GTX 1650 with 4 GB VRAM run it with partial GPU offloading. This is a tested configuration, not a minimum-memory guarantee.

## Windows

Prerequisites: Python 3.10+ on PATH; JDK 17+ to build/run Sentinel. GPU acceleration requires a working driver. From the project root:

```powershell
.\scripts\setup-qwen.ps1 --launch
```

Setup reuses a running loopback Ollama server, or downloads the official portable Ollama 0.40.13 release when missing, verifies its published SHA-256 digest, and starts a hidden loopback server. It downloads the model when missing, checks a real JSON completion, and writes the six provider settings to ignored `.env`. It then builds Sentinel with the Maven Wrapper, runs the Java tests, checks application configuration, and runs a real completion and validated model plan through Sentinel's own adapter. `--launch` opens the GUI only after all checks pass; omit it for setup and verification alone. A failed build or integration check returns nonzero. The runtime/model/logs stay under ignored `runtime/ollama`. No administrator access or global installation is required. Python and JDK installation are prerequisites, not silent system changes. Set `JAVA_HOME` to your JDK if it is not on PATH.

[requirements.txt](../requirements.txt) lists prerequisites and entry points. There are no third-party Python dependencies; `pip install -r requirements.txt` alone does not install Ollama or run setup. The cross-platform entry point is `python scripts/model_setup.py local`; use `--model-only` for Ollama setup without a JDK/build/application check. That diagnostic mode cannot be combined with `--launch` and does not claim a verified Sentinel connection.

The Windows default selects CUDA 12 and disables Vulkan because this fixed the observed GTX 1650 performance problem. To let Ollama choose another backend, pass `--backend auto`. These settings apply when starting a new server; setup reuses an already-running server without changing its configuration. `--model qwen2.5:3b` selects the smaller, approximately 1.9 GB model, whose planning quality has not been evaluated here. `--version` selects a different official runtime version on a fresh install; an existing runtime is reused, not silently upgraded.

After a reboot, rerun setup to start the server and reuse the downloaded model. Alternatively run `demos/start-local-ollama.ps1` in a dedicated terminal. Setup records its child PID in `runtime/ollama/setup-server.pid`; it does not install an auto-start service. Automatic Ollama pruning is disabled to avoid the observed manifest-directory error in this OneDrive workspace.

## Linux

Prerequisites: Python 3.10+, `tar`, `zstd`, and JDK 17+ for Sentinel. For example, on Debian/Ubuntu install those prerequisites using your package manager. Run:

```bash
bash scripts/setup-qwen.sh --launch
```

The Linux setup supports x86-64 and ARM64 and extracts the official `.tar.zst` runtime under `runtime/ollama/linux`, with no root privileges. It uses automatic backend detection by default and starts a detached local process. An existing server is reused. AMD ROCm drivers and optional ROCm runtime packages are not installed by this helper; CPU execution or an existing compatible Ollama installation can be used. Native Ubuntu 24.04.5 x86-64 CPU execution was verified on a fresh VPS; Linux ARM64 and Linux GPU execution remain unverified. See the measured results below.

## Configuration and safe reruns

Setup never executes `.env` as shell code. It preserves unrelated configuration and refuses to overwrite different nonblank model settings unless `--replace-config` is supplied. This flag explicitly switches providers/models and replaces their managed settings; switching to local clears the saved hosted key. Rerunning with the same settings is safe and skips already installed runtime/model downloads. A failed real-model smoke check does not write new configuration. `.env` is stored with owner-only permissions (Windows ACL or Unix mode 600). Downloads and service startup may still have occurred if a later check fails; inspect `runtime/ollama/setup-server.log`.

Process environment values override `.env`. Setup rejects conflicting `SENTINEL_LLM_*` or `SENTINEL_OLLAMA_*` process overrides before downloading or changing configuration, naming only the variable names. Clear those overrides before rerunning. Blank overrides keep lower-priority values. At the same precedence level, the canonical `SENTINEL_LLM_*` values win over legacy Ollama aliases.

| Variable | Meaning |
| --- | --- |
| `SENTINEL_LLM_PROVIDER` | `ollama` (default) or `openai-compatible` |
| `SENTINEL_LLM_BASE_URL` | Ollama root, or compatible API base including version path; exclude `/chat/completions` |
| `SENTINEL_LLM_MODEL` | Installed/local or hosted model identifier |
| `SENTINEL_LLM_API_KEY` | Optional bearer credential; leave empty for local Ollama |
| `SENTINEL_LLM_JSON_MODE` | `true` (default) sends compatible `response_format: json_object`; `false` omits it |
| `SENTINEL_LLM_TOKEN_FIELD` | `max_tokens` (default) or `max_completion_tokens` for compatible APIs |

The legacy `SENTINEL_OLLAMA_URL` and `SENTINEL_OLLAMA_MODEL` still work. Native Ollama always uses its JSON format option and bounded 2048-token output ceiling. Compatible mode uses the selected token field with the same ceiling. The model must support text chat completions and produce the application JSON schema. Providers that require additional parameters or a different protocol need a dedicated adapter.

## Optional hosted Qwen or another compatible provider

Create a key yourself in your provider account. The project cannot issue a Qwen API key. Use the base URL and model name supplied by that account. For Alibaba Model Studio, the endpoint and key must match your workspace and region; see the [official compatibility guide](https://www.alibabacloud.com/help/en/model-studio/compatibility-of-openai-with-dashscope).

Windows (replace the example endpoint/model with your account values):

```powershell
.\scripts\configure-hosted-model.ps1 --base-url https://provider.example/v1 --model provider-model --replace-config
```

Linux:

```bash
bash scripts/configure-hosted-model.sh --base-url https://provider.example/v1 --model provider-model --replace-config
```

Both helpers read the key from the hidden interactive prompt or an existing `SENTINEL_LLM_API_KEY` environment variable. Do not pass keys in command arguments, source code, chat, or logs. The helper saves configuration without making a paid request. HTTPS is required by the hosted helper; the Java adapter also permits HTTP loopback for local testing. If your provider lacks JSON response mode, use `--no-json-mode`; if it expects the newer token field, use `--token-field max_completion_tokens`.

To test the compatible connector without a hosted key or fees, use Ollama's [compatible API](https://docs.ollama.com/api/openai-compatibility) with provider `openai-compatible`, base `http://127.0.0.1:11434/v1`, and model `qwen2.5:7b` via process overrides, then run the live check.

## Architecture and verification

`LLMGateway` is the application-facing port. `ModelGatewayFactory` selects `OllamaAdapter` or `OpenAICompatibleAdapter`; both use `ModelHttpTransport` for one bounded HTTP attempt. The application owns retry/repair budgets. Redirects are refused, credentials are header-only and redacted from configuration diagnostics, provider error bodies are not exposed, and the complete response has a 60-second deadline and 1 MiB bound. The planner validates JSON, tool names, scope and dependencies before accepting a MODEL plan.

Compatibility is protocol-based: Qwen, other Ollama models, and services implementing the supported chat-completions contract can be substituted by configuration. An API with a different wire format requires another `LLMGateway` implementation and a factory option; no changes to planning/domain logic are needed. This does not claim universal support for every proprietary API, reasoning option, multimodal response, or model's output quality.

Setup's 300-second cold-preload smoke test is separate from the application's unchanged 60-second request ceiling. To run deterministic verification:

```text
Maven:  mvnw.cmd --batch-mode verify    (Linux: sh mvnw --batch-mode verify)
Setup:  python -m unittest discover -s scripts -p test_model_setup.py
```

The optional live check tests real completion and a single synthetic-scope validated plan, with no tool execution or hosted charges when using local Ollama. See [verification log](verification-log.md) for measured results and remaining limits.

## Automatic Sentinel connection verification — October 29, 2026

Ran `scripts/setup-qwen.ps1` with `JAVA_HOME=C:\Program Files\Java\jdk-17`. The script reused the existing loopback Ollama server and downloaded Qwen model, passed its real inference warmup, wrote local configuration, built Sentinel, and passed all **404 Java tests**. Application diagnostics confirmed provider `ollama`, URL `http://127.0.0.1:11434`, and model `qwen2.5:7b`. The automatic application check passed a real completion in **2.50 seconds** and produced one validated `logSearch` plan step with `origin=MODEL` in **25.03 seconds**, using one model request. The setup wrapper exited **0**.

This run verifies the Windows setup/reuse path and actual Sentinel connection. A fresh multi-gigabyte download and native Linux execution were not repeated. Setup fixture tests cover runtime checksum validation, starting a hidden server, reusing a running server without installation, conflicting process overrides, automatic application checks, and withholding GUI launch after an integration failure. The GUI launch command is tested with a fake process; the live run omitted `--launch`.

Protocol references: [Ollama generation API](https://docs.ollama.com/api/generate) and [official Qwen2.5 7B model](https://ollama.com/library/qwen2.5:7b).

## Fresh native Linux verification - October 29, 2026 (Toronto)

Ran `bash scripts/test-linux.sh` on a disposable DigitalOcean Ubuntu 24.04.5 x86-64 VPS with 4 shared vCPUs (AMD EPYC 9555P), 12 GiB provisioned RAM and 80 GiB disk. Prerequisites were installed with `apt-get install -y python3 openjdk-17-jdk-headless tar zstd ca-certificates curl`. This was a source-only upload of the current working copy, including uncommitted implementation, without `.env`, credentials, prebuilt JARs, Git history or runtime/model files.

The installer downloaded the official Ollama 0.40.13 Linux archive, verified its checksum, started a detached loopback server, pulled Qwen2.5 7B from scratch, checked real inference and wrote local configuration. All **18 Python setup tests** and **404 Java tests** passed. The packaged application returned a real diagnostic completion in **2.13 seconds**, and a validated model plan in **45.03 seconds across two requests**. The existing 60-second per-request limit was unchanged.

The portable `demos/cli-scenario.py` then passed actual separate-JVM creation, import, rule-plan approval, investigation, cited follow-up, Markdown export and case reopen. The assessment returned **COMPLETE / 7 HIGH**, with one accepted hypothesis, in **36.05 seconds**; the cited follow-up took **32.96 seconds**. The full Linux verification command exited **0** after **283.91 seconds**, excluding VM provisioning and prerequisite installation. These are single synthetic-case measurements, not minimum hardware requirements or a general accuracy guarantee. This headless test did not exercise the Swing GUI or KUMA evaluation.

The first test archive incorrectly used epoch-zero file timestamps, causing Maven's resource copier to skip application resources; preserving source timestamps fixed the test harness. A separate replacement boot timed out during SSH readiness, so the harness was corrected to retry normal boot-time timeouts. No Java application changes or relaxed tests/timeouts were needed for the successful native run.

All three temporary VPS instances and their temporary account SSH keys were deleted and independently audited afterward. The account had **zero remaining Droplets**, no temporary test SSH keys, and its pre-existing HF key remained intact. Total provisioned lifetimes were about 14.30 minutes across attempts; estimated compute cost was **US$0.02925 before tax**, based on the authenticated US$0.12274/hour rate and recorded lifetimes, not a confirmed invoice. No paid backups, snapshots or extra storage were requested. Ollama listened on `127.0.0.1:11434`.
