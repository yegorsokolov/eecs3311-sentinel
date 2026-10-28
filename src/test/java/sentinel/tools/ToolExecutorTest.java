package sentinel.tools;

import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import sentinel.config.AppConfig.ExecutionLimits;
import sentinel.data.*;
import sentinel.domain.*;
import static org.junit.jupiter.api.Assertions.*;
import static sentinel.tools.ToolException.Code.*;

class ToolExecutorTest {
    private static final Instant START = Instant.parse("2026-10-22T12:00:00Z");
    private static final CaseScope ACCOUNT = new CaseScope("demo", "v1", "alice", START, START.plusSeconds(60));
    @TempDir Path temp;

    private ToolCall call(String tool, Map<String, String> changes) {
        var args = new HashMap<>(Map.of("datasetId", "demo", "datasetVersion", "v1", "accountId", "alice",
                "start", START.toString(), "end", START.plusSeconds(60).toString(), "limit", "10"));
        args.putAll(changes);
        return new ToolCall(UUID.randomUUID(), tool, args);
    }
    private ToolCall lookup(String indicator, String version) {
        return new ToolCall(UUID.randomUUID(), "indicatorLookup", Map.of("indicator", indicator, "feedVersion", version));
    }
    private ToolScope scope() { return ToolScope.account(ACCOUNT, "feed-1", Set.of("192.0.2.10")); }
    private ToolResult empty() { return new ToolResult("EMPTY", List.of(), "v1", 0, false); }
    private ToolException fails(ToolException.Code code, org.junit.jupiter.api.function.Executable action) {
        var ex = assertThrows(ToolException.class, action);
        assertEquals(code, ex.code());
        return ex;
    }
    private DatasetStore store() {
        var store = new DatasetStore("demo", Clock.fixed(START, ZoneOffset.UTC), Duration.ofDays(30));
        store.ingest(new ImportBatch(List.of(
                new Event("a", START, "login", "alice", "192.0.2.10", "host", "failure", "file:///sample#/0", "pending"),
                new Event("b", START.plusSeconds(60), "login", "alice", "192.0.2.10", "host", "success", "file:///sample#/1", "pending"),
                new Event("c", START, "login", "bob", "192.0.2.11", null, null, "file:///sample#/2", "pending"))));
        return store;
    }

    @Test void localRegistryDispatchesAllCommandsAndPreservesTypedData() throws Exception {
        var store = store();
        Path feed = temp.resolve("feed.json");
        java.nio.file.Files.writeString(feed, """
                {"feedVersion":"feed-1","records":[{"indicator":"192.0.2.10","verdict":"MALICIOUS",
                "source":"synthetic","observedAt":"2026-10-22T12:00:00Z"}]}
                """);
        store.loadIndicatorFeed(feed);
        try (var executor = ToolExecutor.local(store)) {
            var session = executor.openSession(scope());
            var search = session.execute(call("logSearch", Map.of()));
            assertEquals(List.of("a", "b"), search.evidence().stream().map(Evidence::sourceEventId).toList());
            var activity = session.execute(call("userActivity", Map.of("limit", "1")));
            assertEquals("PARTIAL", activity.status());
            assertEquals(2, activity.activitySummary().totalEvents());
            assertEquals(1, activity.activitySummary().returnedEvents());
            var indicator = session.execute(lookup("192.0.2.10", "feed-1"));
            assertEquals("MALICIOUS", indicator.indicatorMatch().verdict());
            assertEquals("feed-1", indicator.sourceVersion());
            assertEquals(1, indicator.evidence().size());
            assertEquals(3, session.attemptsUsed());
            assertEquals(5, session.attemptsRemaining());
            assertEquals("v1", store.currentVersion());
            assertEquals(3, store.events("v1").size());
        }
    }

    @Test void unscopedExecutionAndNullScopeAreRefused() {
        try (var executor = ToolExecutor.local(store())) {
            fails(SCOPE_REQUIRED, () -> executor.execute(call("logSearch", Map.of())));
            fails(SCOPE_REQUIRED, () -> executor.openSession(null));
        }
    }

    @Test void registryIsCopiedAndOnlyApprovedNamesAreAccepted() {
        var commands = new HashMap<String, ToolCommand>();
        commands.put("logSearch", call -> empty());
        try (var executor = new ToolExecutor(commands)) {
            commands.clear();
            executor.validate(call("logSearch", Map.of()));
            fails(UNKNOWN_TOOL, () -> executor.validate(call("userActivity", Map.of())));
            fails(UNKNOWN_TOOL, () -> executor.validate(new ToolCall(UUID.randomUUID(), "shell", Map.of())));
        }
        assertThrows(IllegalArgumentException.class, () -> new ToolExecutor(Map.of("shell", call -> empty())));
    }

    @Test void planningValidationHasNoDispatchBudgetOrDuplicateSideEffects() {
        var invoked = new AtomicInteger();
        try (var executor = new ToolExecutor(Map.of("logSearch", call -> { invoked.incrementAndGet(); return empty(); }))) {
            var session = executor.openSession(scope());
            for (int i = 0; i < 12; i++) session.validate(call("logSearch", Map.of()));
            assertEquals(0, invoked.get());
            assertEquals(0, session.attemptsUsed());
            session.execute(call("logSearch", Map.of()));
            assertEquals(1, invoked.get());
        }
    }

    @Test void invalidArgumentsNeverInvokeCommandsOrConsumeAttempts() {
        var invoked = new AtomicInteger();
        try (var executor = new ToolExecutor(Map.of("logSearch", call -> { invoked.incrementAndGet(); return empty(); },
                "indicatorLookup", call -> { invoked.incrementAndGet(); return empty(); }))) {
            var session = executor.openSession(scope());
            fails(INVALID_ARGUMENT, () -> session.execute(null));
            fails(INVALID_ARGUMENT, () -> session.execute(call("logSearch", Map.of("limit", "0"))));
            fails(INVALID_ARGUMENT, () -> session.execute(call("logSearch", Map.of("sql", "anything"))));
            fails(INVALID_ARGUMENT, () -> session.execute(lookup("https://bad.example", "feed-1")));
            assertEquals(0, session.attemptsUsed());
            assertEquals(0, invoked.get());
        }
    }

    @ParameterizedTest @ValueSource(strings={"dataset", "version", "before", "after", "account", "no-account"})
    void rejectsOutOfScopeQueriesBeforeDispatch(String variant) {
        var args = new HashMap<>(call("logSearch", Map.of()).args());
        switch (variant) {
            case "dataset" -> args.put("datasetId", "other");
            case "version" -> args.put("datasetVersion", "v0");
            case "before" -> args.put("start", START.minusSeconds(1).toString());
            case "after" -> args.put("end", START.plusSeconds(61).toString());
            case "account" -> args.put("accountId", "bob");
            case "no-account" -> args.remove("accountId");
            default -> fail();
        }
        try (var executor = new ToolExecutor(Map.of("logSearch", call -> { fail("Must not dispatch"); return empty(); }))) {
            var session = executor.openSession(scope());
            fails(OUT_OF_SCOPE, () -> session.execute(new ToolCall(UUID.randomUUID(), "logSearch", args)));
            assertEquals(0, session.attemptsUsed());
        }
    }

    @Test void narrowIntervalsAndExactBoundariesAreAllowed() {
        try (var executor = ToolExecutor.local(store())) {
            var session = executor.openSession(scope());
            assertEquals(1, session.execute(call("logSearch", Map.of("end", START.toString()))).evidence().size());
            assertEquals(1, session.execute(call("userActivity", Map.of(
                    "start", START.plusSeconds(60).toString()))).evidence().size());
        }
    }

    @Test void indicatorScopesRequireMatchingSourceIpAndRejectDomainEventSearch() {
        var ip = new CaseScope("demo", "v1", "192.0.2.10", START, START.plusSeconds(60));
        try (var executor = ToolExecutor.local(store())) {
            var session = executor.openSession(ToolScope.indicator(ip, "feed-1"));
            var args = new HashMap<>(call("logSearch", Map.of("sourceIp", "192.0.2.10")).args());
            args.remove("accountId");
            assertEquals(2, session.execute(new ToolCall(UUID.randomUUID(), "logSearch", args)).evidence().size());
            fails(OUT_OF_SCOPE, () -> session.execute(call("logSearch", Map.of("sourceIp", "192.0.2.11"))));
            fails(OUT_OF_SCOPE, () -> session.execute(call("logSearch", Map.of())));
            fails(OUT_OF_SCOPE, () -> session.execute(call("userActivity", Map.of())));
            var domain = new CaseScope("demo", "v1", "evil.example", START, START.plusSeconds(60));
            fails(OUT_OF_SCOPE, () -> executor.openSession(ToolScope.indicator(domain, "feed-1"))
                    .execute(call("logSearch", Map.of("sourceIp", "evil.example"))));
        }
    }

    @Test void normalizedIndicatorTargetAndTrustedEvidenceUsePinnedFeed() {
        var domain = new CaseScope("demo", "v1", "EVIL.EXAMPLE.", START, START.plusSeconds(60));
        var observed = new HashSet<>(Set.of("192.0.2.10"));
        var trusted = ToolScope.account(ACCOUNT, "feed-1", observed);
        observed.add("untrusted.example");
        try (var executor = new ToolExecutor(Map.of("indicatorLookup", call -> empty()))) {
            var target = executor.openSession(ToolScope.indicator(domain, "feed-1"));
            target.validate(lookup(" evil.example ", "feed-1"));
            var session = executor.openSession(trusted);
            session.validate(lookup("192.0.2.10", "feed-1"));
            fails(OUT_OF_SCOPE, () -> session.execute(lookup("untrusted.example", "feed-1")));
            fails(OUT_OF_SCOPE, () -> session.execute(lookup("192.0.2.10", "feed-2")));
            fails(OUT_OF_SCOPE, () -> executor.openSession(ToolScope.account(ACCOUNT, null, Set.of("192.0.2.10")))
                    .execute(lookup("192.0.2.10", "feed-1")));
            assertThrows(UnsupportedOperationException.class, () -> trusted.evidenceIndicators().clear());
        }
    }

    @Test void explicitAccountKindDoesNotGuessDomainLookingAccountNames() {
        var account = new CaseScope("demo", "v1", "service.example", START, START.plusSeconds(60));
        try (var executor = new ToolExecutor(Map.of("logSearch", call -> empty(), "indicatorLookup", call -> empty()))) {
            var session = executor.openSession(ToolScope.account(account, "feed-1", Set.of()));
            session.validate(call("logSearch", Map.of("accountId", "service.example")));
            fails(OUT_OF_SCOPE, () -> session.validate(lookup("service.example", "feed-1")));
        }
    }

    @Test void canonicalKeysIgnoreUuidOrderingOffsetsAndNumericSpelling() {
        ToolCall original = call("logSearch", Map.of("eventId", "a"));
        var args = new LinkedHashMap<String, String>();
        original.args().entrySet().stream().sorted(Map.Entry.<String, String>comparingByKey().reversed())
                .forEach(entry -> args.put(entry.getKey(), entry.getValue()));
        args.put("start", "2026-10-22T08:00:00-04:00");
        args.put("limit", "0010");
        assertEquals(original.key(), new ToolCall(UUID.randomUUID(), "logSearch", args).key());
        assertNotEquals(original.key(), call("logSearch", Map.of("eventId", "b")).key());
        assertNotEquals(original.key(), call("userActivity", Map.of("eventId", "a")).key());
        assertEquals(lookup("EVIL.EXAMPLE.", "feed-1").key(), lookup(" evil.example ", "feed-1").key());
        assertEquals(lookup("2001:db8::1", "feed-1").key(), lookup("2001:0DB8:0:0:0:0:0:1", "feed-1").key());
        assertNotEquals(lookup("evil.example", "feed-1").key(), lookup("evil.example", "feed-2").key());
        assertNotEquals(call("logSearch", Map.of("accountId", "Alice")).key(), call("logSearch", Map.of()).key());
        assertNotEquals(new ToolCall(UUID.randomUUID(), "x", Map.of("a", "b:c", "d", "e")).key(),
                new ToolCall(UUID.randomUUID(), "x", Map.of("a", "b", "c:d", "e")).key());
    }

    @Test void duplicatesNeverDispatchOrSpendAdditionalAttempts() {
        var count = new AtomicInteger();
        try (var executor = new ToolExecutor(Map.of("logSearch", call -> { count.incrementAndGet(); return empty(); }))) {
            var session = executor.openSession(scope());
            session.execute(call("logSearch", Map.of()));
            fails(DUPLICATE_CALL, () -> session.execute(call("logSearch", Map.of("limit", "0010",
                    "start", "2026-10-22T08:00:00-04:00"))));
            assertEquals(1, count.get());
            assertEquals(1, session.attemptsUsed());
            executor.openSession(scope()).execute(call("logSearch", Map.of()));
            assertEquals(2, count.get());
        }
    }

    @Test void budgetCapsEightAttemptsAndSessionsDoNotShareCounters() {
        var count = new AtomicInteger();
        try (var executor = new ToolExecutor(Map.of("logSearch", call -> { count.incrementAndGet(); return empty(); }))) {
            var session = executor.openSession(scope());
            for (int i = 0; i < 8; i++) session.execute(call("logSearch", Map.of("eventId", "e" + i)));
            fails(BUDGET_EXHAUSTED, () -> session.execute(call("logSearch", Map.of("eventId", "ninth"))));
            assertEquals(8, count.get());
            assertEquals(0, session.attemptsRemaining());
            var fresh = executor.openSession(scope());
            assertEquals(0, fresh.attemptsUsed());
            fresh.execute(call("logSearch", Map.of("eventId", "fresh")));
            assertEquals(1, fresh.attemptsUsed());
        }
    }

    @Test void explicitTransientFailureAllowsOneBudgetedRetry() {
        var count = new AtomicInteger();
        try (var executor = new ToolExecutor(Map.of("logSearch", call -> {
            if (count.incrementAndGet() == 1) throw new ToolException(TRANSIENT_FAILURE, "Temporary.");
            return empty();
        }))) {
            var session = executor.openSession(scope());
            assertEquals("EMPTY", session.execute(call("logSearch", Map.of())).status());
            assertEquals(2, session.attemptsUsed());
            assertEquals(2, count.get());
            fails(DUPLICATE_CALL, () -> session.execute(call("logSearch", Map.of())));
        }
    }

    @Test void repeatedTransientFailureStopsAfterOneRetry() {
        var count = new AtomicInteger();
        try (var executor = new ToolExecutor(Map.of("logSearch", call -> {
            count.incrementAndGet(); throw new ToolException(TRANSIENT_FAILURE, "Temporary.");
        }))) {
            var session = executor.openSession(scope());
            fails(TRANSIENT_FAILURE, () -> session.execute(call("logSearch", Map.of())));
            assertEquals(2, count.get());
            assertEquals(2, session.attemptsUsed());
            fails(DUPLICATE_CALL, () -> session.execute(call("logSearch", Map.of())));
        }
    }

    @Test void retriesCannotExceedRemainingBudgetAndCanBeDisabled() {
        var count = new AtomicInteger();
        ToolCommand transientCommand = call -> { count.incrementAndGet(); throw new ToolException(TRANSIENT_FAILURE, "Temporary."); };
        try (var executor = new ToolExecutor(Map.of("logSearch", transientCommand), 1, Duration.ofSeconds(1), 1)) {
            var session = executor.openSession(scope());
            fails(BUDGET_EXHAUSTED, () -> session.execute(call("logSearch", Map.of())));
            assertEquals(1, count.get());
            assertEquals(1, session.attemptsUsed());
        }
        try (var executor = new ToolExecutor(Map.of("logSearch", transientCommand), 2, Duration.ofSeconds(1), 0)) {
            var session = executor.openSession(scope());
            fails(TRANSIENT_FAILURE, () -> session.execute(call("logSearch", Map.of())));
            assertEquals(1, session.attemptsUsed());
        }
    }

    @Test void permanentFailureIsNotRetriedAndUnexpectedMessagesAreSanitized() {
        var count = new AtomicInteger();
        try (var executor = new ToolExecutor(Map.of("logSearch", call -> {
            count.incrementAndGet(); throw new ToolException(UNKNOWN_VERSION, "Unknown version.");
        }))) {
            var session = executor.openSession(scope());
            fails(UNKNOWN_VERSION, () -> session.execute(call("logSearch", Map.of())));
            assertEquals(1, count.get());
            fails(DUPLICATE_CALL, () -> session.execute(call("logSearch", Map.of())));
        }
        try (var executor = new ToolExecutor(Map.of("logSearch", call -> { throw new IllegalStateException("private marker"); }))) {
            var ex = fails(EXECUTION_FAILED, () -> executor.openSession(scope()).execute(call("logSearch", Map.of())));
            assertFalse(ex.getMessage().contains("private marker"));
            assertNull(ex.getCause());
        }
        try (var executor = new ToolExecutor(Map.of("logSearch", call -> null))) {
            fails(EXECUTION_FAILED, () -> executor.openSession(scope()).execute(call("logSearch", Map.of())));
        }
    }

    @Test void timeoutsDoNotRetryAndQueuedCancelledCallsNeverRunLate() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var count = new AtomicInteger();
        ToolCommand stubborn = call -> {
            count.incrementAndGet();
            entered.countDown();
            boolean done = false;
            while (!done) {
                try { release.await(); done = true; }
                catch (InterruptedException ignored) { /* Deliberately emulate an uncooperative trusted command. */ }
            }
            return empty();
        };
        try (var executor = new ToolExecutor(Map.of("logSearch", stubborn), 8, Duration.ofMillis(200), 1)) {
            var session = executor.openSession(scope());
            fails(TIMEOUT, () -> session.execute(call("logSearch", Map.of("eventId", "first"))));
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            // The sole worker is still held. These queued submissions time out and must be purged/cancelled.
            fails(TIMEOUT, () -> session.execute(call("logSearch", Map.of("eventId", "second"))));
            fails(TIMEOUT, () -> session.execute(call("logSearch", Map.of("eventId", "third"))));
            assertEquals(1, count.get());
            assertEquals(3, session.attemptsUsed());
            fails(DUPLICATE_CALL, () -> session.execute(call("logSearch", Map.of("eventId", "first"))));
        } finally { release.countDown(); }
    }

    @Test void callerInterruptionCancelsAttemptAndPreservesInterruptFlag() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var finished = new CountDownLatch(1);
        var code = new AtomicReference<ToolException.Code>();
        var interrupted = new AtomicBoolean();
        try (var executor = new ToolExecutor(Map.of("logSearch", call -> {
            entered.countDown();
            try { release.await(); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            return empty();
        }))) {
            var session = executor.openSession(scope());
            Thread caller = new Thread(() -> {
                try { session.execute(call("logSearch", Map.of())); }
                catch (ToolException ex) { code.set(ex.code()); interrupted.set(Thread.currentThread().isInterrupted()); }
                finally { finished.countDown(); }
            });
            caller.setDaemon(true);
            caller.start();
            try {
                assertTrue(entered.await(2, TimeUnit.SECONDS));
                caller.interrupt();
                assertTrue(finished.await(2, TimeUnit.SECONDS));
                assertEquals(INTERRUPTED, code.get());
                assertTrue(interrupted.get());
                assertEquals(1, session.attemptsUsed());
            } finally { release.countDown(); caller.interrupt(); }
        }
    }

    @Test void alreadyInterruptedCallerDoesNotDispatchOrSpendBudget() {
        try (var executor = ToolExecutor.local(store())) {
            var session = executor.openSession(scope());
            Thread.currentThread().interrupt();
            try {
                fails(INTERRUPTED, () -> session.execute(call("logSearch", Map.of())));
                assertTrue(Thread.currentThread().isInterrupted());
                assertEquals(0, session.attemptsUsed());
            } finally { Thread.interrupted(); }
        }
    }

    @Test void closedExecutorRejectsNewAndExistingSessions() {
        var executor = ToolExecutor.local(store());
        var session = executor.openSession(scope());
        executor.close();
        fails(EXECUTOR_CLOSED, () -> executor.openSession(scope()));
        fails(EXECUTOR_CLOSED, () -> session.execute(call("logSearch", Map.of())));
        assertEquals(0, session.attemptsUsed());
        executor.close();
    }

    @Test void validatesInjectedLimitsAndScopeContext() {
        Map<String, ToolCommand> registry = Map.of("logSearch", call -> empty());
        assertThrows(IllegalArgumentException.class, () -> new ToolExecutor(registry, 0, Duration.ofSeconds(1), 1));
        assertThrows(IllegalArgumentException.class, () -> new ToolExecutor(registry, 9, Duration.ofSeconds(1), 1));
        assertThrows(IllegalArgumentException.class, () -> new ToolExecutor(registry, 1, Duration.ZERO, 1));
        assertThrows(IllegalArgumentException.class, () -> new ToolExecutor(registry, 1, Duration.ofSeconds(11), 1));
        assertThrows(IllegalArgumentException.class, () -> new ToolExecutor(registry, 1, Duration.ofSeconds(1), 2));
        assertThrows(IllegalArgumentException.class, () -> new ToolExecutor(registry, 1, Duration.ofSeconds(1), -1));
        assertThrows(IllegalArgumentException.class, () -> ToolScope.account(ACCOUNT, " ", Set.of()));
        assertThrows(IllegalArgumentException.class, () -> ToolScope.indicator(ACCOUNT, "feed-1"));
        try (var executor = new ToolExecutor(registry, new ExecutionLimits(2, 2, 6, 1, 60, 0))) {
            assertEquals(2, executor.openSession(scope()).attemptsRemaining());
        }
    }

    @Test void closingCancelsInFlightResultEvenWhenCommandIgnoresInterruption() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var finished = new CountDownLatch(1);
        var code = new AtomicReference<ToolException.Code>();
        var executor = new ToolExecutor(Map.of("logSearch", call -> {
            entered.countDown();
            while (true) {
                try { release.await(); return empty(); }
                catch (InterruptedException ignored) { }
            }
        }));
        var session = executor.openSession(scope());
        Thread caller = new Thread(() -> {
            try { session.execute(call("logSearch", Map.of())); }
            catch (ToolException ex) { code.set(ex.code()); }
            finally { finished.countDown(); }
        });
        caller.setDaemon(true);
        caller.start();
        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            executor.close();
            assertTrue(finished.await(2, TimeUnit.SECONDS));
            assertEquals(EXECUTOR_CLOSED, code.get());
            assertEquals(1, session.attemptsUsed());
        } finally {
            release.countDown();
            caller.interrupt();
            executor.close();
        }
    }

    @Test void concurrentDuplicateRequestsDispatchOnlyOnce() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var count = new AtomicInteger();
        var callers = Executors.newFixedThreadPool(2);
        try (var executor = new ToolExecutor(Map.of("logSearch", call -> {
            count.incrementAndGet();
            entered.countDown();
            try { release.await(); }
            catch (InterruptedException ex) { throw new ToolException(INTERRUPTED, "Interrupted."); }
            return empty();
        }))) {
            var session = executor.openSession(scope());
            Future<ToolResult> first = callers.submit(() -> session.execute(call("logSearch", Map.of())));
            try {
                assertTrue(entered.await(2, TimeUnit.SECONDS));
                Future<ToolException.Code> second = callers.submit(() -> {
                    try { session.execute(call("logSearch", Map.of())); return null; }
                    catch (ToolException ex) { return ex.code(); }
                });
                release.countDown();
                assertEquals("EMPTY", first.get(2, TimeUnit.SECONDS).status());
                assertEquals(DUPLICATE_CALL, second.get(2, TimeUnit.SECONDS));
                assertEquals(1, count.get());
                assertEquals(1, session.attemptsUsed());
            } finally { release.countDown(); }
        } finally { callers.shutdownNow(); }
    }

    @Test void normalizedIndicatorDuplicatesAreSuppressed() {
        var domain = new CaseScope("demo", "v1", "evil.example", START, START.plusSeconds(60));
        try (var executor = new ToolExecutor(Map.of("indicatorLookup", call -> empty()))) {
            var session = executor.openSession(ToolScope.indicator(domain, "feed-1"));
            session.execute(lookup(" EVIL.EXAMPLE. ", "feed-1"));
            fails(DUPLICATE_CALL, () -> session.execute(lookup("evil.example", "feed-1")));
            assertEquals(1, session.attemptsUsed());
        }
    }

    @Test void newlyAcceptedEvidenceCanAuthorizeEnrichmentWithoutResettingBudgetsOrDuplicates() {
        try (var executor = new ToolExecutor(Map.of("logSearch", call -> empty(), "indicatorLookup", call -> empty()))) {
            var session = executor.openSession(ToolScope.account(ACCOUNT, "feed-1", Set.of()));
            session.execute(call("logSearch", Map.of()));
            fails(OUT_OF_SCOPE, () -> session.validate(lookup("new.example", "feed-1")));
            session.acceptEvidenceIndicators(Set.of("NEW.EXAMPLE."));
            session.execute(lookup("new.example", "feed-1"));
            assertEquals(2, session.attemptsUsed());
            fails(DUPLICATE_CALL, () -> session.execute(call("logSearch", Map.of())));
            fails(OUT_OF_SCOPE, () -> session.execute(lookup("new.example", "feed-2")));
            assertThrows(IllegalArgumentException.class,
                    () -> session.acceptEvidenceIndicators(Set.of("unaccepted.example", "invalid")));
            fails(OUT_OF_SCOPE, () -> session.validate(lookup("unaccepted.example", "feed-1")));
        }
    }}
