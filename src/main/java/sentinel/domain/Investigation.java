package sentinel.domain;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Single-investigation aggregate. Each successful operation updates one revision.
 * Callers supply semantically validated plans/assessments; this class guards lifecycle.
 * No tools, model calls or disk writes occur here.
 */
public final class Investigation {
    private final UUID id;
    private final String goal;
    private final Clock clock;
    private int caseVersion;
    private CaseStatus status;
    private CaseScope scope;
    private Plan plan;
    private Assessment assessment;
    private final List<Evidence> evidence = new ArrayList<>();
    private final List<ConversationEntry> conversation = new ArrayList<>();
    private final List<AuditEntry> history = new ArrayList<>();
    private String modelVersion;
    private String promptVersion;
    private String policyVersion;
    private final TargetType targetType;
    private final String feedVersion;

    public Investigation(Request request) {
        this(request, Clock.systemUTC());
    }

    public Investigation(Request request, Clock clock) {
        this(request, clock, TargetType.ACCOUNT, null);
    }

    public Investigation(Request request, TargetType targetType, String feedVersion) {
        this(request, Clock.systemUTC(), targetType, feedVersion);
    }

    public Investigation(Request request, Clock clock, TargetType targetType, String feedVersion) {
        this.targetType = DomainValidation.required(targetType, "targetType");
        if (feedVersion != null) DomainValidation.requiredText(feedVersion, "feedVersion");
        this.feedVersion = feedVersion;
        DomainValidation.required(request, "request");
        this.clock = DomainValidation.required(clock, "clock");
        id = UUID.randomUUID();
        goal = request.goal();
        scope = request.scope();
        if (targetType == TargetType.INDICATOR && scope != null) Indicator.normalize(scope.target());
        status = CaseStatus.DRAFT;
        history.add(new AuditEntry(clock.instant(), "CREATED", "DRAFT", List.of()));
    }

    private Investigation(CaseSnapshot snapshot, Clock clock) {
        this.clock = DomainValidation.required(clock, "clock");
        id = snapshot.id();
        goal = snapshot.goal();
        caseVersion = snapshot.caseVersion();
        status = snapshot.status();
        scope = snapshot.scope();
        plan = snapshot.plan();
        evidence.addAll(snapshot.evidence());
        assessment = snapshot.assessment();
        conversation.addAll(snapshot.conversation());
        history.addAll(snapshot.history());
        modelVersion = snapshot.modelVersion();
        promptVersion = snapshot.promptVersion();
        policyVersion = snapshot.policyVersion();
        targetType = snapshot.targetType();
        feedVersion = snapshot.feedVersion();
        if (status == CaseStatus.RUNNING) {
            status = CaseStatus.PAUSED;
            history.add(new AuditEntry(clock.instant(), "RESTORED", "RUNNING_TO_PAUSED", List.of()));
            caseVersion++;
        }
    }

    public static Investigation restore(CaseSnapshot snapshot) {
        return restore(snapshot, Clock.systemUTC());
    }

    public static Investigation restore(CaseSnapshot snapshot, Clock clock) {
        return new Investigation(DomainValidation.required(snapshot, "snapshot"), clock);
    }

    /** Complete or correct scope while still awaiting a plan. */
    public void clarifyScope(CaseScope clarifiedScope) {
        requireState(CaseStatus.DRAFT);
        DomainValidation.required(clarifiedScope, "scope");
        if (targetType == TargetType.INDICATOR) Indicator.normalize(clarifiedScope.target());
        scope = clarifiedScope;
        history.add(new AuditEntry(clock.instant(), "SCOPE_CLARIFIED", "DRAFT", List.of()));
        caseVersion++;
    }

    /** A completed case whose revised scope needs planning returns to DRAFT (SM01). */
    public void reviseScope(CaseScope revisedScope) {
        requireState(CaseStatus.COMPLETED);
        if (targetType == TargetType.INDICATOR && revisedScope != null) Indicator.normalize(revisedScope.target());
        scope = revisedScope; // null explicitly means clarification is required
        plan = null;
        status = CaseStatus.DRAFT;
        markAssessmentStale();
        history.add(new AuditEntry(clock.instant(), "SCOPE_REVISED", "DRAFT", List.of()));
        caseVersion++;
    }

    /**
     * Accept a plan after PlanValidator succeeds. Revalidation never starts execution.
     * During a run, the engine may replace remaining steps within its revision budget.
     */
    public void setPlan(Plan validatedPlan) {
        DomainValidation.required(validatedPlan, "plan");
        if (scope == null) throw new IllegalStateException("Scope clarification is required.");
        if (status == CaseStatus.FAILED) throw new IllegalStateException("FAILED is terminal.");
        plan = validatedPlan;
        if (status != CaseStatus.RUNNING) status = CaseStatus.READY;
        markAssessmentStale();
        history.add(new AuditEntry(clock.instant(), "PLAN_ACCEPTED", status.name(), List.of()));
        caseVersion++;
    }

    /** Attach a validated result atomically; same-ID conflicts never partially mutate the case. */
    public void addEvidence(ToolResult result) {
        DomainValidation.required(result, "result");
        if (scope == null || status == CaseStatus.FAILED) throw new IllegalStateException("Case cannot accept evidence.");
        if (!java.util.Set.of("SUCCESS", "PARTIAL", "EMPTY", "UNKNOWN", "STALE").contains(result.status())) {
            throw new IllegalArgumentException("Cannot attach a failed tool result.");
        }
        if ((result.status().equals("EMPTY") || result.status().equals("UNKNOWN")) && !result.evidence().isEmpty()) {
            throw new IllegalArgumentException("Empty or unmatched results cannot contain evidence.");
        }
        var staged = new java.util.LinkedHashMap<UUID, Evidence>();
        evidence.forEach(item -> staged.put(item.id(), item));
        for (Evidence item : result.evidence()) {
            if (!result.sourceVersion().equals(item.datasetVersion())) {
                throw new IllegalArgumentException("Evidence version differs from result source.");
            }
            Evidence previous = staged.putIfAbsent(item.id(), item);
            if (previous != null && !previous.equals(item)) throw new IllegalArgumentException("Conflicting evidence ID.");
        }
        boolean added = staged.size() != evidence.size();
        Assessment nextAssessment = added ? stale(assessment) : assessment;
        var ids = result.evidence().stream().map(Evidence::id).distinct().toList();
        var entry = new AuditEntry(clock.instant(), "EVIDENCE_RECORDED", result.status(), ids);
        var nextHistory = new ArrayList<>(history);
        nextHistory.add(entry);
        // Validate all aggregate references before publishing any changes.
        new CaseSnapshot(1, caseVersion, id, goal, status, scope, plan, List.copyOf(staged.values()),
                nextAssessment, conversation, nextHistory, modelVersion, promptVersion, policyVersion, targetType, feedVersion);
        evidence.clear();
        evidence.addAll(staged.values());
        assessment = nextAssessment;
        history.add(entry);
        caseVersion++;
    }

    /** Store already validated analysis; this never marks a run completed by itself. */
    public void setAssessment(Assessment acceptedAssessment) {
        requireState(CaseStatus.RUNNING);
        DomainValidation.required(acceptedAssessment, "assessment");
        if (acceptedAssessment.completeness() == Completeness.STALE) {
            throw new IllegalArgumentException("Cannot accept a stale assessment as new analysis.");
        }
        // Validate references before changing any aggregate state.
        candidate(status, plan, acceptedAssessment, history);
        assessment = acceptedAssessment;
        policyVersion = acceptedAssessment.severity().policyVersion();
        history.add(new AuditEntry(clock.instant(), "ASSESSMENT_ACCEPTED",
                acceptedAssessment.completeness().name(), List.of()));
        caseVersion++;
    }

    /**
     * Record an action, or apply a guarded STATE_CHANGED audit event.
     * READY is reached only through setPlan (explicit successful validation).
     * PAUSED is reached only by restoring a saved RUNNING case.
     */
    public void recordAction(String action, String outcome, List<UUID> ids) {
        record(new AuditEntry(clock.instant(), action, outcome, ids));
    }

    public void record(AuditEntry entry) {
        DomainValidation.required(entry, "entry");
        CaseStatus target = status;
        if (entry.action().equals("STATE_CHANGED")) {
            try {
                target = CaseStatus.valueOf(entry.outcome());
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("Unknown lifecycle target.");
            }
            boolean allowed = (status == CaseStatus.READY && target == CaseStatus.RUNNING)
                    || (status == CaseStatus.RUNNING && (target == CaseStatus.COMPLETED
                        || target == CaseStatus.PARTIAL || target == CaseStatus.FAILED));
            if (!allowed) throw new IllegalStateException("Invalid lifecycle transition: " + status + " -> " + target);
            if (target == CaseStatus.COMPLETED
                    && (assessment == null || assessment.completeness() != Completeness.COMPLETE)) {
                throw new IllegalStateException("Completion requires accepted complete analysis.");
            }
        }
        var nextHistory = new ArrayList<>(history);
        nextHistory.add(entry);
        candidate(target, plan, assessment, nextHistory);
        status = target;
        history.add(entry);
        caseVersion++;
    }

    public CaseSnapshot snapshot() {
        return candidate(status, plan, assessment, history);
    }

    private CaseSnapshot candidate(CaseStatus candidateStatus, Plan candidatePlan,
                                   Assessment candidateAssessment, List<AuditEntry> candidateHistory) {
        return new CaseSnapshot(1, caseVersion, id, goal, candidateStatus, scope,
                candidatePlan, evidence, candidateAssessment, conversation, candidateHistory,
                modelVersion, promptVersion, policyVersion, targetType, feedVersion);
    }

    private void requireState(CaseStatus expected) {
        if (status != expected) throw new IllegalStateException("Operation requires " + expected + ".");
    }

    /** Validate before the importer mutates its store. Running/failed cases cannot change datasets. */
    public void validateImport(String datasetId) {
        if (scope == null || !scope.datasetId().equals(datasetId)) {
            throw new IllegalStateException("Import requires the active case dataset.");
        }
        if (status == CaseStatus.RUNNING || status == CaseStatus.FAILED) {
            throw new IllegalStateException("Cannot import into a running or failed case.");
        }
    }

    /** New versions invalidate plans and analysis; failed/duplicate-only reads preserve them. */
    public void recordImport(String datasetId, Summary summary) {
        validateImport(datasetId);
        DomainValidation.required(summary, "summary");
        boolean changed = summary.successful() && !scope.datasetVersion().equals(summary.datasetVersion());
        if (changed) {
            scope = new CaseScope(scope.datasetId(), summary.datasetVersion(), scope.target(), scope.start(), scope.end());
            plan = null;
            status = CaseStatus.DRAFT;
            markAssessmentStale();
        }
        history.add(new AuditEntry(clock.instant(), summary.successful() ? "IMPORT_COMPLETED" : "IMPORT_REJECTED",
                summary.successful() ? summary.datasetVersion() + ":accepted=" + summary.accepted()
                        + ":duplicates=" + summary.duplicates() : "INVALID_INPUT", List.of()));
        caseVersion++;
    }

    private static Assessment stale(Assessment value) {
        return value == null || value.completeness() == Completeness.STALE ? value
                : new Assessment(value.timeline(), value.hypotheses(), value.severity(),
                        value.recommendations(), Completeness.STALE);
    }

    private void markAssessmentStale() {
        if (assessment != null && assessment.completeness() != Completeness.STALE) {
            assessment = new Assessment(assessment.timeline(), assessment.hypotheses(), assessment.severity(),
                    assessment.recommendations(), Completeness.STALE);
        }
    }
}
