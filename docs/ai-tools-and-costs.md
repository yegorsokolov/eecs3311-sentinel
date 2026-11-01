# AI tools, models and costs

## Cost basis

I use Codex through an existing ChatGPT subscription costing US$22.60 per month, or C$32.60 per month, as reported on November 1, 2026. These are my reported subscription amounts in alternative currencies, not two separate charges or a newly calculated exchange rate. I also reported three US$20 Codex credit top-ups purchased during development, totalling US$60. I used Codex for multiple other projects and tasks during the same period, so the full subscription and purchased-credit amounts should not be charged exclusively to Sentinel.

The earlier figure of 1,200 credits above the included allowance was my reported estimate, not independently metered Sentinel consumption. A subsequent review of retained local history provides the usage estimate below. Included allowance and purchased credits cannot be separated reliably by project from those records. Purchases, credit-equivalent model usage and actual billed consumption are therefore reported separately rather than added together.

## History-based Sentinel usage estimate

The estimate covers October 20 - November 1, 2026, with a fixed history cutoff of November 1 at 19:26:44 UTC. Requests are attributed to Sentinel when their session working directory is the Sentinel repository. Other working directories are included only in the aggregate comparison; their project names and conversation contents are not reproduced.

| Recorded Sentinel model | Input tokens, including cached input | Cached input tokens (subset) | Output tokens | Standard-rate credit equivalent |
| --- | ---: | ---: | ---: | ---: |
| GPT-6.1 Sol | 97,509,268 | 94,489,472 | 458,031 | 501.72 |
| GPT-6 Astra | 27,848,667 | 27,244,416 | 144,642 | 1,012.98 |
| Total | 125,357,935 | 121,733,888 | 602,673 | 1,514.70 |

These totals cover 857 recorded Sentinel responses. Repeated context reads count on each request; they are not unique source-code or document tokens. Cached input is already included in input, and reasoning output is a subset of output, so neither is added again. The overall input-plus-output total is 125,960,608 tokens.

The estimate uses per-response token-usage records, deduplicated by response identity rather than summing cumulative session counters. Recorded model names identify the model selected for those requests; they do not independently verify the underlying deployed model snapshot. Automatic approval-review requests and records without an identified priced model are excluded from the credit comparison. Sessions without token records, deleted/remote history, other devices and other ChatGPT use are not reconstructed.

### Credit calculation and limits

Using the [official Standard credit rates](https://learn.chatgpt.com/docs/pricing) consulted on November 1, 2026:

- GPT-6.1 Sol: 50 credits per million uncached input tokens, 2.5 per million cached input tokens and 250 per million output tokens.
- GPT-6 Astra: 250 credits per million uncached input tokens, 25 per million cached input tokens and 1,250 per million output tokens.

Formula: `((input - cached input) � input rate + cached input � cached rate + output � output rate) / 1,000,000`.

The resulting approximately 1,515 credits is a Standard-rate usage equivalent, including requests covered by the subscription. It is not a verified purchased-credit debit, an API invoice or an additional charge on top of the US$60 purchases. Historical rates and speed settings may differ; the retained records do not establish a billable speed multiplier for every response. Applying current Standard rates consistently provides a comparison, not a reconstruction of final billing.

The same method gives approximately 1,878 credit-equivalent units for other recorded projects/tasks in the comparison period. Sentinel therefore represents 44.6% of the combined model-weighted usage, with 55.4% attributed to other work. Working directory is an attribution heuristic: mixed-purpose sessions can contain work outside their initial directory. Missing history and differing use of included allowance can also change the true billed allocation.

A live account check showed 445.30 purchased credits remaining at the time of review. This is an account-wide balance, not a Sentinel balance. Historical balance snapshots include increases and fluctuations; summing only decreases would not establish reliable project consumption. No exact Sentinel-only purchased-credit total is claimed, and the earlier 1,200-credit estimate is not treated as verified project metering.

## Subscription allocation estimate

Applying the 44.6% model-weighted share gives an illustrative Sentinel allocation of US$10.09 / C$14.55 for one full month of the existing subscription. Prorating a 30-day month to the 13 calendar days from October 20 through November 1 gives approximately US$4.37 / C$6.31. The actual subscription billing-cycle dates were not used; this is a transparent planning allocation rather than an invoice calculation.

These alternatives must not be added together. The full monthly fee remains US$22.60 / C$32.60, shared with other projects and tasks. The US$60 top-up figure remains a purchase total; its remaining balance and project split mean it is not a verified Sentinel consumption cost. No complete project cost is claimed, and no separate OpenAI API-key billing information has been supplied.

## Tools, models and usage

| Tool/platform | Model | Purpose and major tasks | Usage basis | Cost basis |
| --- | --- | --- | --- | --- |
| Codex desktop | Recorded GPT-6.1 Sol and GPT-6 Astra requests; earlier records may be incomplete | Implementation, debugging, tests, review and documentation assistance | About 1,515 Standard-rate credit-equivalent units from 857 retained Sentinel responses; included/purchased split unknown | Shared subscription US$22.60 / C$32.60 monthly; illustrative 13-day Sentinel allocation US$4.37 / C$6.31. US$60 top-ups were purchased during development, with other work sharing the account. |
| Ollama 0.40.13 | Qwen2.5 7B Instruct Q4_K_M, tag `qwen2.5:7b` | Sentinel investigation planning, grounded synthesis/recommendations and cited follow-up | One direct diagnostic: 37 prompt / 10 output tokens; total Qwen usage unmeasured | No hosted inference fees; existing hardware and electricity unallocated. |

Local Qwen is separate from the subscription used to develop Sentinel. It runs on existing hardware through Ollama; no hosted inference API fee was incurred in the recorded local verification. Portable Ollama with CUDA 12 passed real completion, validated planning, synthesis and cited follow-up. Qwen occupies about 4.7 GB; installed manifest digest: `845dbda0ea48ed749caafd9e6037047aa19acfcfd82e704d7ca97d631a0b697e`.

Temporary Linux verification also incurred an estimated US$0.02925 before tax in compute costs across attempts; this was a provisioned-lifetime estimate, not an invoice. The temporary instances and keys were removed. See the [verification log](verification-log.md) for environment, timings and limitations.

I direct, review and approve the documentation, with Codex assistance in drafting, editing and checking consistency with the implementation. See the [collaboration log](ai-collaboration-log.md) for representative contributions. Project-wide Qwen tokens, hardware/electricity and exact project-level billed Codex credits remain unmeasured. The figures above supersede the earlier statement that the subscription price and all local-history token totals were unavailable.
