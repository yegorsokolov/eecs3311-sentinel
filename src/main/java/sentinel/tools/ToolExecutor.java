package sentinel.tools;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import sentinel.config.AppConfig.ExecutionLimits;
import sentinel.data.DatasetStore;
import sentinel.data.IndicatorLookupQuery;
import sentinel.data.LogSearchQuery;
import sentinel.domain.*;
import static sentinel.tools.ToolException.Code.*;

/**
 * Allow-listed Command invoker. Each session binds immutable trusted scope and owns its
 * attempt/duplicate state; validation is side-effect free. One daemon worker and one queue
 * slot bound resources even when a trusted command ignores interruption after a timeout.
 */
public final class ToolExecutor implements AutoCloseable {
    private static final Set<String> ALLOWED = Set.of("logSearch", "userActivity", "indicatorLookup");
    private final Map<String, ToolCommand> commands;
    private final int maxAttempts;
    private final long timeoutNanos;
    private final int transientRetries;
    private final ThreadPoolExecutor worker;
    private final Set<Future<?>> inFlight = ConcurrentHashMap.newKeySet();

    public ToolExecutor(Map<String, ToolCommand> commands) {
        this(commands, 8, Duration.ofSeconds(10), 1);
    }

    public ToolExecutor(Map<String, ToolCommand> commands, ExecutionLimits limits) {
        this(commands, Objects.requireNonNull(limits, "limits").toolAttempts(),
                Duration.ofSeconds(limits.toolTimeoutSeconds()), limits.transientRetries());
    }

    /** Limits may tighten, but cannot exceed the accepted Stage 1 tool ceilings. */
    public ToolExecutor(Map<String, ToolCommand> commands, int maxAttempts,
                        Duration timeout, int transientRetries) {
        this.commands = Map.copyOf(Objects.requireNonNull(commands, "commands"));
        if (!ALLOWED.containsAll(this.commands.keySet())) {
            throw new IllegalArgumentException("Registry contains an unsupported tool name.");
        }
        Objects.requireNonNull(timeout, "timeout");
        if (maxAttempts < 1 || maxAttempts > 8 || timeout.isZero() || timeout.isNegative()
                || timeout.compareTo(Duration.ofSeconds(10)) > 0 || transientRetries < 0 || transientRetries > 1) {
            throw new IllegalArgumentException("Tool limits must fit the Stage 1 ceilings.");
        }
        this.maxAttempts = maxAttempts;
        this.timeoutNanos = timeout.toNanos();
        this.transientRetries = transientRetries;
        worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(1), task -> {
            Thread thread = new Thread(task, "sentinel-local-tool");
            thread.setDaemon(true);
            return thread;
        }, new ThreadPoolExecutor.AbortPolicy());
    }

    public static ToolExecutor local(DatasetStore store) {
        return new ToolExecutor(Map.of("logSearch", new LogSearchCommand(store),
                "userActivity", new UserActivityCommand(store), "indicatorLookup", new IndicatorLookupCommand(store)));
    }

    /** No unscoped execution path is permitted. Use openSession with trusted case context. */
    public ToolResult execute(ToolCall call) {
        throw failure(SCOPE_REQUIRED, "Tool execution requires a scoped session.");
    }

    /** Schema/registry validation only: planning can validate without executing or spending attempts. */
    public void validate(ToolCall call) {
        if (call == null) throw failure(INVALID_ARGUMENT, "Tool call is required.");
        if (!commands.containsKey(call.toolName())) throw failure(UNKNOWN_TOOL, "Tool is not registered.");
        switch (call.toolName()) {
            case "logSearch" -> LogSearchQuery.parse(call);
            case "userActivity" -> LogSearchQuery.parseActivity(call);
            case "indicatorLookup" -> IndicatorLookupQuery.parse(call);
            default -> throw failure(UNKNOWN_TOOL, "Tool is not registered.");
        }
    }

    /** A new session represents a new explicit run/query budget; scope cannot be widened in place. */
    public Session openSession(ToolScope scope) {
        if (scope == null) throw failure(SCOPE_REQUIRED, "Tool execution requires case scope.");
        if (worker.isShutdown()) throw failure(EXECUTOR_CLOSED, "Tool executor is closed.");
        return new Session(scope);
    }

    public final class Session {
        private final ToolScope scope;
        private final Set<String> seen = new HashSet<>();
        private final Set<String> evidenceIndicators;
        private int attempts;

        private Session(ToolScope scope) {
            this.scope = scope;
            evidenceIndicators = new HashSet<>(scope.evidenceIndicators());
        }

        /**
         * Trusted application boundary after accepting new in-scope case evidence.
         * Never pass proposed model arguments here. Updates preserve attempts and duplicate history.
         */
        public synchronized void acceptEvidenceIndicators(Set<String> indicators) {
            if (worker.isShutdown()) throw failure(EXECUTOR_CLOSED, "Tool executor is closed.");
            Set<String> normalized = Objects.requireNonNull(indicators, "indicators").stream()
                    .map(Indicator::normalize).collect(java.util.stream.Collectors.toUnmodifiableSet());
            evidenceIndicators.addAll(normalized);
        }

        public synchronized int attemptsUsed() { return attempts; }
        public synchronized int attemptsRemaining() { return maxAttempts - attempts; }

        /** Shared by planning and execution; never invokes a command or consumes a budget. */
        public synchronized void validate(ToolCall call) {
            ToolExecutor.this.validate(call);
            if ("indicatorLookup".equals(call.toolName())) {
                var query = IndicatorLookupQuery.parse(call);
                if (!Objects.equals(scope.feedVersion(), query.feedVersion())) {
                    throw outside("Indicator lookup requires the selected feed version.");
                }
                boolean target = scope.targetKind() == ToolScope.TargetKind.INDICATOR
                        && scope.normalizedTarget().equals(query.indicator());
                if (!target && !evidenceIndicators.contains(query.indicator())) {
                    throw outside("Indicator is outside the target and accepted evidence.");
                }
                return;
            }
            var query = "logSearch".equals(call.toolName())
                    ? LogSearchQuery.parse(call) : LogSearchQuery.parseActivity(call);
            CaseScope bounds = scope.caseScope();
            if (!bounds.datasetId().equals(query.datasetId()) || !bounds.datasetVersion().equals(query.version())
                    || query.start().isBefore(bounds.start()) || query.end().isAfter(bounds.end())) {
                throw outside("Query must use the case dataset/version and lie within its interval.");
            }
            if (scope.targetKind() == ToolScope.TargetKind.ACCOUNT) {
                if (!bounds.target().equals(query.filters().get("accountId"))) {
                    throw outside("Account queries require an exact target account filter.");
                }
            } else {
                // Event schema has a sourceIp field, but no domain field. Never silently broaden a domain search.
                String sourceIp = query.filters().get("sourceIp");
                if (!"logSearch".equals(call.toolName()) || sourceIp == null) {
                    throw outside("Indicator event search requires a source IP filter.");
                }
                String normalized;
                try { normalized = Indicator.normalize(sourceIp); }
                catch (IllegalArgumentException ex) { throw outside("Source IP must match the target indicator."); }
                if (!isIp(normalized) || !scope.normalizedTarget().equals(normalized)) {
                    throw outside("Source IP must match the target indicator.");
                }
            }
        }

        /** Duplicate suppression covers all completed/failed submissions; only explicit transient failures retry. */
        public synchronized ToolResult execute(ToolCall call) {
            if (worker.isShutdown()) throw failure(EXECUTOR_CLOSED, "Tool executor is closed.");
            validate(call);
            if (Thread.currentThread().isInterrupted()) throw failure(INTERRUPTED, "Tool execution interrupted.");
            String key = call.key();
            if (seen.contains(key)) throw failure(DUPLICATE_CALL, "Equivalent call was already attempted in this session.");
            if (attempts >= maxAttempts) throw failure(BUDGET_EXHAUSTED, "Tool attempt budget exhausted.");
            seen.add(key);
            long started = System.nanoTime();
            for (int retry = 0; ; retry++) {
                if (attempts >= maxAttempts) throw failure(BUDGET_EXHAUSTED, "Tool attempt budget exhausted.");
                Future<ToolResult> future;
                try {
                    future = worker.submit(() -> commands.get(call.toolName()).execute(call));
                } catch (RejectedExecutionException ex) {
                    // Nothing was submitted; a later admission may try again without bypassing a completed call.
                    if (retry == 0) seen.remove(key);
                    throw failure(worker.isShutdown() ? EXECUTOR_CLOSED : EXECUTOR_BUSY,
                            "Tool executor cannot accept another call.");
                }
                inFlight.add(future);
                if (worker.isShutdown()) future.cancel(true);
                attempts++;
                try {
                    ToolResult result = future.get(timeoutNanos, TimeUnit.NANOSECONDS);
                    if (result == null) throw failure(EXECUTION_FAILED, "Tool returned no result.");
                    return new ToolResult(result.status(), result.evidence(), result.sourceVersion(),
                            Math.max(0, (System.nanoTime() - started) / 1_000_000),
                            result.truncated(), result.activitySummary(), result.indicatorMatch());
                } catch (TimeoutException ex) {
                    cancel(future);
                    // A timeout may leave an interrupt-ignoring worker busy; never overlap a retry.
                    throw failure(TIMEOUT, "Tool attempt timed out.");
                } catch (InterruptedException ex) {
                    cancel(future);
                    Thread.currentThread().interrupt();
                    throw failure(INTERRUPTED, "Tool execution interrupted.");
                } catch (CancellationException ex) {
                    throw failure(EXECUTOR_CLOSED, "Tool execution was cancelled.");
                } catch (ExecutionException ex) {
                    if (ex.getCause() instanceof ToolException toolFailure) {
                        if (toolFailure.code() == TRANSIENT_FAILURE && retry < transientRetries) continue;
                        throw toolFailure;
                    }
                    throw failure(EXECUTION_FAILED, "Tool execution failed.");
                } finally {
                    inFlight.remove(future);
                }
            }
        }
    }

    private void cancel(Future<?> future) {
        future.cancel(true);
        worker.purge();
    }

    private static boolean isIp(String value) {
        return value.contains(":") || value.matches("[0-9]+(?:\\.[0-9]+){3}");
    }

    private static ToolException outside(String message) { return failure(OUT_OF_SCOPE, message); }
    private static ToolException failure(ToolException.Code code, String message) { return new ToolException(code, message); }

    @Override
    public void close() {
        for (Runnable pending : worker.shutdownNow()) {
            if (pending instanceof Future<?> future) future.cancel(true);
        }
        inFlight.forEach(future -> future.cancel(true));
    }
}
