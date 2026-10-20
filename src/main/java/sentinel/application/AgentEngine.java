package sentinel.application;

import java.nio.file.Path;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import sentinel.domain.*;
import sentinel.planning.*;
import sentinel.tools.*;
import sentinel.analysis.*;
import sentinel.events.*;

/** Stage 1 contract scaffold; business behavior is pending. */
public class AgentEngine {
    private final PlanningStrategy planner;
    private final ToolExecutor tools;
    private final AnalysisService analysis;
    private final EventBus events;
    public AgentEngine(PlanningStrategy planner, ToolExecutor tools, AnalysisService analysis, EventBus events) {
        this.planner = Objects.requireNonNull(planner);
        this.tools = Objects.requireNonNull(tools);
        this.analysis = Objects.requireNonNull(analysis);
        this.events = Objects.requireNonNull(events);
    }
    public Plan plan(Request request, Investigation investigation) {
        throw new UnsupportedOperationException("Skeleton only: implementation pending");
    }
    public Assessment run(Investigation investigation) {
        throw new UnsupportedOperationException("Skeleton only: implementation pending");
    }
    public ToolResult query(Investigation investigation, ToolCall call) {
        throw new UnsupportedOperationException("Skeleton only: implementation pending");
    }
    public Answer ask(Investigation investigation, String question) {
        throw new UnsupportedOperationException("Skeleton only: implementation pending");
    }
}
