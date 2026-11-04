package sentinel.application;

import java.nio.file.Path;
import java.util.*;
import sentinel.domain.*;
import sentinel.persistence.*;
import sentinel.data.*;
import sentinel.reporting.*;
import sentinel.events.*;

/** Single-active-case MVC controller shared by the future GUI and CLI. */
public class AgentController {
    private final AgentEngine engine;
    private final CaseRepository repository;
    private final LogImporter importer;
    private final ReportService reporter;
    private final EventBus events;
    private Investigation active;

    public AgentController(AgentEngine engine, CaseRepository repository, LogImporter importer, ReportService reporter, EventBus events) {
        this.engine = Objects.requireNonNull(engine);
        this.repository = Objects.requireNonNull(repository);
        this.importer = Objects.requireNonNull(importer);
        this.reporter = Objects.requireNonNull(reporter);
        this.events = Objects.requireNonNull(events);
        if (engine.datasetStore() != importer.store()) throw new IllegalArgumentException("Controller services must share one dataset store.");
    }

    public synchronized Investigation start(Request request) { return start(request, TargetType.ACCOUNT, null); }

    public synchronized Investigation start(Request request, TargetType targetType, String feedVersion) { return start(request,targetType,feedVersion,false); }

    public synchronized Investigation start(Request request, TargetType targetType, String feedVersion, boolean ruleApproved) {
        Objects.requireNonNull(request, "request");
        if (active != null && active.snapshot().status() == CaseStatus.RUNNING) {
            throw new IllegalStateException("Finish the active run before starting another case.");
        }
        if (request.scope() != null) ensureDataset(request.scope());
        var candidate = new Investigation(request, targetType, feedVersion);
        if (active != null) engine.invalidateSessions(active.snapshot().id());
        active = candidate;
        if(ruleApproved)engine.acceptRulePlan(active);else engine.plan(request, active);
        return active;
    }

    public synchronized Plan clarifyScope(UUID id, CaseScope scope) {
        var investigation = requireActive(id);
        ensureDataset(scope);
        investigation.clarifyScope(scope);
        return engine.plan(new Request(investigation.snapshot().goal(), scope), investigation);
    }

    /** Explicit approval keeps rule fallback on the same case instead of silently starting another case. */
    public synchronized Plan acceptRulePlan(UUID id) { return engine.acceptRulePlan(requireActive(id)); }

    public synchronized void revise(UUID id, CaseScope scope) { ensureDataset(scope); requireActive(id).reviseScope(scope); engine.invalidateSessions(id); }
    public synchronized Path export(UUID id,Path path,boolean overwrite) { return reporter.export(requireActive(id),path,overwrite); }

    public synchronized CaseSnapshot snapshot(UUID id) { return requireActive(id).snapshot(); }

    public synchronized Summary importLogs(UUID id, Path path) {
        var investigation = requireActive(id);
        synchronized (investigation) {
            investigation.validateImport(importer.store().datasetId());
            String before = investigation.snapshot().scope().datasetVersion();
            Summary summary = importer.importFile(path);
            investigation.recordImport(importer.store().datasetId(), summary);
            if (!before.equals(investigation.snapshot().scope().datasetVersion())) engine.invalidateSessions(id);
            events.publish(new ProgressEvent("IMPORT", summary.successful() ? "SUCCESS" : "REJECTED", List.of()));
            return summary;
        }
    }

    public synchronized ToolResult execute(UUID id, ToolCall call) { return engine.query(requireActive(id), call); }
    public Assessment run(UUID id) { Investigation selected; synchronized(this){selected=requireActive(id);} return engine.run(selected); }
    public synchronized Plan resume(UUID id) { return engine.revalidate(requireActive(id)); }
    public void cancel(UUID id) { engine.cancel(id); }
    public synchronized void inventory(Set<String> hosts) { engine.inventory(hosts); }
    public synchronized List<CaseSummary> listCases() { return repository.list(); }
    public synchronized CaseSnapshot current() { if(active==null)throw new IllegalStateException("No active case."); return active.snapshot(); }
    public synchronized Plan replan(UUID id) { var i=requireActive(id);return engine.plan(new Request(i.snapshot().goal(),i.snapshot().scope()),i); }
    public synchronized Answer ask(UUID id, String question) { return engine.ask(requireActive(id), question); }

    public synchronized int save(UUID id) {
        return repository.save(requireActive(id).snapshot());
    }

    public synchronized Investigation open(UUID id) {
        Investigation candidate = repository.load(Objects.requireNonNull(id));
        if (candidate == null || !candidate.snapshot().id().equals(id)) throw new IllegalArgumentException("Repository returned another case.");
        if (active != null) engine.invalidateSessions(active.snapshot().id());
        active = candidate;
        events.publish(new ProgressEvent("CASE", "OPENED", List.of()));
        return active;
    }

    public synchronized Path export(UUID id, Path path) { return reporter.export(requireActive(id), path); }

    private Investigation requireActive(UUID id) {
        if (active == null || !active.snapshot().id().equals(id)) throw new IllegalArgumentException("Case is not active.");
        return active;
    }

    private void ensureDataset(CaseScope scope) {
        Objects.requireNonNull(scope, "scope");
        if (!importer.store().datasetId().equals(scope.datasetId())) throw new IllegalArgumentException("Unknown dataset.");
        importer.store().events(scope.datasetVersion());
    }
}
