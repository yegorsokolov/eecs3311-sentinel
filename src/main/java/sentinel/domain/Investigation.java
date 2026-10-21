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

    public Investigation(Request request) {
        this(request, Clock.systemUTC());
    }

    public Investigation(Request request, Clock clock) {
        DomainValidation.required(request, "request");
        this.clock = DomainValidation.required(clock, "clock");
        id = UUID.randomUUID();
        goal = request.goal();
        scope = request.scope();
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
        scope = clarifiedScope;
        history.add(new AuditEntry(clock.instant(), "SCOPE_CLARIFIED", "DRAFT", List.of()));
        caseVersion++;
    }

    /** A completed case whose revised scope needs planning returns to DRAFT (SM01). */
    public void reviseScope(CaseScope revisedScope) {
        requireState(CaseStatus.COMPLETED);
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

    /** Evidence ingestion will be connected with the validated tool-result contract. */
    public void addEvidence(ToolResult result) {
        throw new UnsupportedOperationException("Evidence ingestion is implemented with tool execution.");
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
                modelVersion, promptVersion, policyVersion);
    }

    private void requireState(CaseStatus expected) {
        if (status != expected) throw new IllegalStateException("Operation requires " + expected + ".");
    }

    private void markAssessmentStale() {
        if (assessment != null && assessment.completeness() != Completeness.STALE) {
            assessment = new Assessment(assessment.timeline(), assessment.hypotheses(), assessment.severity(),
                    assessment.recommendations(), Completeness.STALE);
        }
    }
}
