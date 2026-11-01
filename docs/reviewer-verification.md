# Reproducing Sentinel for review

The course's full Project Instructions and separate Stage 2 Instructions do not prescribe the instructor's operating system, hardware, cloud provider, or an exact marking command. Do not assume a Linux VPS is required by the assignment.

The Stage 2 Instructions require a public repository with complete source, GUI and CLI, at least one working LLM integration, a README with prerequisites/install/configuration/launch/examples, dependency/build files, safe sample data, and accessible feature demonstration videos collectively showing both interfaces. Stage 2 requires basic verification; the full deterministic test and KUMA behavioral-validation requirements remain Stage 3 work. The final full-project instructions require test-running instructions, test counts/results, meaningful failure analysis, improvements, and retesting.

## Windows or desktop Linux

Install Python 3.10+ and a full JDK 17+. Linux also needs `tar` and `zstd`. See [model setup](model-setup.md) for disk requirements and configuration options.

Windows: `powershell -ExecutionPolicy Bypass -File scripts/setup-qwen.ps1 --launch`

Linux desktop: `bash scripts/setup-qwen.sh --launch`

Setup prepares local Qwen, builds/tests Sentinel, and verifies a real completion and model-generated validated plan before opening the GUI. Local inference needs no paid API key.

## Headless Linux check

On a disposable Ubuntu 24.04 machine, install prerequisites first:

```bash
sudo apt-get update
sudo apt-get install -y python3 openjdk-17-jdk-headless tar zstd ca-certificates curl
bash scripts/test-linux.sh
```

The check prints OS/memory/disk details, runs Python setup tests, installs/starts portable Ollama, downloads and warms Qwen, configures `.env`, runs Maven verification, and checks real completion and validated model planning through Sentinel. It then runs a synthetic CLI investigation, checks COMPLETE / 7 HIGH and a cited follow-up, and verifies Markdown export and case reopen. Each run uses separate storage under `runtime/test-runs/`. Any failing stage exits nonzero. It does not claim GUI verification or replace KUMA behavioral evaluation. Do not use `--launch` on a server without a graphical desktop.

This command passed on a fresh Ubuntu 24.04.5 x86-64 VPS on October 29, 2026 (Toronto), including all 18 setup tests, 404 Java tests and the real Qwen/CLI checks. The tested VPS had 4 shared vCPUs and 12 GiB RAM; this is an observed configuration, not a minimum specification. See [model setup](model-setup.md) for timings, cleanup and remaining platform limits.
