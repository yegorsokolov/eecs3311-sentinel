# Analysis contract analysis-v1

AnalysisService builds the authoritative grounded prompt from canonical scoped evidence (maximum 24 items), fixed severity and prior hypotheses. Source data is untrusted.

The compact wire shape is ShortSynthesis(version, hypothesis, recommendation). Hypothesis includes text, ref, against refs, qualitative confidence and limitation. Recommendation includes COLLECT/REVIEW/ESCALATE action, reason, ref, priority and prerequisite. Prompt-local E1-style refs resolve to immutable evidence UUIDs before validation and storage. The full Synthesis record form is also accepted for compatible providers.

One schema/citation repair shares the six-call operation budget; no invented citations or model severity are accepted. Invalid/unavailable AI output preserves deterministic timeline/severity as PARTIAL. Recommendations require analyst approval and cannot execute response actions.
