# AI tools, models and costs

## Cost basis

Development uses Codex through Yegor's existing ChatGPT subscription, as confirmed by Yegor on 2026-10-22. No additional project-specific AI charges are expected while usage remains within the subscription allowance. The existing subscription has a recurring cost; its price and an allocation to this project have not been supplied. No separate API-token billing is expected for this workflow.

OpenAI distinguishes included subscription usage from API-key billing. Additional purchased credits or API-key usage can incur charges; these are not planned project expenses. See [official OpenAI pricing documentation](https://learn.chatgpt.com/docs/pricing) (consulted 2026-10-22). This records the expected cost basis, not a verified billing statement.

Sentinel is designed to use Qwen2.5 7B Instruct locally through Ollama on existing hardware. No hosted inference API fees or per-token provider charges are expected for that local setup. Electricity and existing hardware costs are not measured or allocated here. Runtime integration is pending; record the actual installed model version and available usage measurements when integrated.

## Development tools and usage

| Tool/platform | Model | Purpose and major tasks | Input tokens | Output tokens | Total tokens | Money cost basis |
| --- | --- | --- | --- | --- | --- | --- |
| Codex desktop | GPT-6 family; exact deployed identifier unavailable in the development record | Initial skeleton, requirements mapping and project structure | Unavailable | Unavailable | Unavailable | Existing subscription; no additional project charge expected within allowance |
| Codex desktop | GPT-6 family; exact deployed identifier unavailable in the development record | Step 1 configuration, bootstrap, Maven setup and MVP tests | Unavailable | Unavailable | Unavailable | Same subscription basis |
| Codex desktop | GPT-6 family; exact deployed identifier unavailable in the development record | Step 2 core domain validation, immutable import batches and MVP tests | Unavailable | Unavailable | Unavailable | Same subscription basis |
| Codex desktop | GPT-6 family; exact deployed identifier unavailable in the development record | Step 3 lifecycle, snapshots, restoration and MVP tests | Unavailable | Unavailable | Unavailable | Same subscription basis |
| Codex desktop | GPT-6 family; exact deployed identifier unavailable in the development record | Step 4 importer/store, dependency packaging, samples, tests and verification instructions | Unavailable | Unavailable | Unavailable | Same subscription basis |
| Ollama (planned Sentinel runtime) | Qwen2.5 7B Instruct; configured tag qwen2.5:7b | Planned investigation planning and analysis | Not yet measured | Not yet measured | Not yet measured | Local execution; no provider inference charges expected |

Token usage still occurs with a subscription and a local model. Unavailable counts are not zero and are not inferred from monetary cost. Update this record if measured usage, the exact model identifier or an additional expense becomes available.

Representative AI contributions and human review are documented in [the collaboration log](ai-collaboration-log.md).
