package sentinel.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Immutable schema-v1 aggregate state, including the original analyst goal.
 * caseVersion is the in-memory revision; repository save conflict handling comes later.
 * Model/prompt/policy metadata may be null until those components have been used.
 */
public record CaseSnapshot(int schemaVersion, int caseVersion, UUID id, String goal,
                           CaseStatus status, CaseScope scope, Plan plan, List<Evidence> evidence,
                           Assessment assessment, List<ConversationEntry> conversation,
                           List<AuditEntry> history, String modelVersion, String promptVersion,
                           String policyVersion, TargetType targetType, String feedVersion) {
    /** Source-compatible constructor for cases created before explicit target/feed selection. */
    public CaseSnapshot(int schemaVersion, int caseVersion, UUID id, String goal, CaseStatus status,
                        CaseScope scope, Plan plan, List<Evidence> evidence, Assessment assessment,
                        List<ConversationEntry> conversation, List<AuditEntry> history,
                        String modelVersion, String promptVersion, String policyVersion) {
        this(schemaVersion, caseVersion, id, goal, status, scope, plan, evidence, assessment,
                conversation, history, modelVersion, promptVersion, policyVersion, TargetType.ACCOUNT, null);
    }

    public CaseSnapshot {
        DomainValidation.required(targetType, "targetType");
        if (targetType == TargetType.INDICATOR && scope != null) Indicator.normalize(scope.target());
        if (feedVersion != null) DomainValidation.requiredText(feedVersion, "feedVersion");
        if (schemaVersion != 1) throw new IllegalArgumentException("Unsupported snapshot schema.");
        if (caseVersion < 0) throw new IllegalArgumentException("caseVersion must not be negative.");
        DomainValidation.required(id, "id");
        DomainValidation.requiredText(goal, "goal");
        DomainValidation.required(status, "status");
        evidence = List.copyOf(DomainValidation.required(evidence, "evidence"));
        conversation = List.copyOf(DomainValidation.required(conversation, "conversation"));
        history = List.copyOf(DomainValidation.required(history, "history"));
        if (status == CaseStatus.DRAFT && plan != null) {
            throw new IllegalArgumentException("DRAFT must not contain a ready plan.");
        }
        if (status != CaseStatus.DRAFT && (scope == null || plan == null)) {
            throw new IllegalArgumentException("A non-draft case requires scope and plan.");
        }
        if (status == CaseStatus.COMPLETED && (assessment == null
                || (assessment.completeness() != Completeness.COMPLETE
                    && assessment.completeness() != Completeness.STALE))) {
            throw new IllegalArgumentException("COMPLETED requires a complete or subsequently stale assessment.");
        }
        Set<UUID> ids = new HashSet<>();
        for (Evidence item : evidence) {
            if (!ids.add(item.id())) throw new IllegalArgumentException("Duplicate evidence IDs.");
        }
        for (AuditEntry entry : history) requireReferences(entry.evidenceIds(), ids);
        for (ConversationEntry entry : conversation) {
            if (entry.answer() != null) requireReferences(entry.answer().evidenceIds(), ids);
        }
        if (assessment != null) {
            for (Hypothesis hypothesis : assessment.hypotheses()) {
                requireReferences(hypothesis.supportingEvidenceIds(), ids);
                requireReferences(hypothesis.contradictingEvidenceIds(), ids);
            }
        }
    }

    private static void requireReferences(List<UUID> references, Set<UUID> ids) {
        if (!ids.containsAll(references)) {
            throw new IllegalArgumentException("Snapshot contains unknown evidence references.");
        }
    }
}
