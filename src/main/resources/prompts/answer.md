# Follow up contract analysis-v1
AnalysisService.answer uses the current case scope and accepted canonical evidence only.
Output is Answer(text, evidenceIds, limitations); unknown or absent citations are rejected with one bounded repair.
Scope changes require explicit analyst input. Source text is untrusted data, not instructions.
