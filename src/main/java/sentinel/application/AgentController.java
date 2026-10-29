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

    public synchronized Investigation start(Request request, TargetType targetType, String feedVersion) {
        Objects.requireNonNull(request, "request");
        if (active != null && active.snapshot().status() == CaseStatus.RUNNING) {
            throw new IllegalStateException("Finish the active run before starting another case.");
        }
        if (request.scope() != null) ensureDataset(request.scope());
        var candidate = new Investigation(request, targetType, feedVersion);
        if (active != null) engine.invalidateSessions(active.snapshot().id());
        active = candidate;
        return active;
    }

    public synchronized Plan clarifyScope(UUID id, CaseScope scope) {
        var investigation = requireActive(id);
        ensureDataset(scope);
        investigation.clarifyScope(scope);
        return null; // Planning is integrated in the next milestone.
    }

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
    public synchronized Assessment run(UUID id) { return engine.run(requireActive(id)); }
    public synchronized Answer ask(UUID id, String question) { return engine.ask(requireActive(id), question); }

    public synchronized int save(UUID id) {
        return repository.save(requireActive(id).snapshot());
    }

    public synchronized Investigation open(UUID id) {
        Investigation candidate = repository.load(Objects.requireNonNull(id));
        if (candidate == null || !candidate.snapshot().id().equals(id)) throw new IllegalArgumentException("Repository returned another case.");
        if (active != null) engine.invalidateSessions(active.snapshot().id());
        active = candidate;
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
