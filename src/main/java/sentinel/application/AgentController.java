package sentinel.application;

import java.nio.file.Path;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import sentinel.domain.*;
import sentinel.persistence.*;
import sentinel.data.*;
import sentinel.reporting.*;
import sentinel.events.*;

/** Stage 1 contract scaffold; business behavior is pending. */
public class AgentController {
    private final AgentEngine engine;
    private final CaseRepository repository;
    private final LogImporter importer;
    private final ReportService reporter;
    private final EventBus events;
    public AgentController(AgentEngine engine, CaseRepository repository, LogImporter importer, ReportService reporter, EventBus events) {
        this.engine = Objects.requireNonNull(engine);
        this.repository = Objects.requireNonNull(repository);
        this.importer = Objects.requireNonNull(importer);
        this.reporter = Objects.requireNonNull(reporter);
        this.events = Objects.requireNonNull(events);
    }
    public Investigation start(Request request) {
        throw new UnsupportedOperationException("Skeleton only: implementation pending");
    }
    public Summary importLogs(UUID id, Path path) {
        throw new UnsupportedOperationException("Skeleton only: implementation pending");
    }
    public ToolResult execute(UUID id, ToolCall call) {
        throw new UnsupportedOperationException("Skeleton only: implementation pending");
    }
    public Assessment run(UUID id) {
        throw new UnsupportedOperationException("Skeleton only: implementation pending");
    }
    public Answer ask(UUID id, String question) {
        throw new UnsupportedOperationException("Skeleton only: implementation pending");
    }
    public int save(UUID id) {
        throw new UnsupportedOperationException("Skeleton only: implementation pending");
    }
    public Investigation open(UUID id) {
        throw new UnsupportedOperationException("Skeleton only: implementation pending");
    }
    public Path export(UUID id, Path path) {
        throw new UnsupportedOperationException("Skeleton only: implementation pending");
    }
}
