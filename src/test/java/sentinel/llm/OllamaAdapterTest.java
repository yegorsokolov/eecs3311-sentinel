package sentinel.llm;

import com.fasterxml.jackson.core.*;
import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import sentinel.domain.Prompt;
import static org.junit.jupiter.api.Assertions.*;
import static sentinel.llm.ModelException.Code.*;

class OllamaAdapterTest {
    private static final String MODEL = "qwen2.5:7b";
    private HttpServer server;
    private ExecutorService workers;
    private URI base;
    private volatile HttpHandler handler;
    private final AtomicInteger requests = new AtomicInteger();
    private final AtomicReference<byte[]> received = new AtomicReference<>();
    private final AtomicReference<String> method = new AtomicReference<>();
    private final AtomicReference<String> contentType = new AtomicReference<>();

    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        workers = Executors.newFixedThreadPool(2, task -> { Thread thread = new Thread(task); thread.setDaemon(true); return thread; });
        server.setExecutor(workers);
        handler = exchange -> reply(exchange, 200, envelope("{\"ok\":true}"), "application/json");
        server.createContext("/api/generate", exchange -> {
            requests.incrementAndGet();
            received.set(exchange.getRequestBody().readAllBytes());
            method.set(exchange.getRequestMethod());
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            handler.handle(exchange);
        });
        server.start();
        base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }
    @AfterEach void stop() { server.stop(0); workers.shutdownNow(); }
    private Prompt prompt() { return new Prompt("test-v1", "planning", "Return JSON for a \"quoted\" goal.\nNo secrets."); }
    private OllamaAdapter adapter() { return new OllamaAdapter(base, MODEL); }
    private String envelope(String response) throws IOException {
        var out = new StringWriter();
        try (var json = new JsonFactory().createGenerator(out)) {
            json.writeStartObject(); json.writeStringField("model", MODEL);
            json.writeStringField("response", response); json.writeBooleanField("done", true);
            json.writeStringField("done_reason", "stop");
            json.writeObjectFieldStart("timing"); json.writeNumberField("tokens", 3); json.writeEndObject();
            json.writeEndObject();
        }
        return out.toString();
    }
    private void reply(HttpExchange exchange, int status, String body, String type) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.sendResponseHeaders(status, bytes.length);
        try (var stream = exchange.getResponseBody()) { stream.write(bytes); }
    }
    private ModelException fails(ModelException.Code code, org.junit.jupiter.api.function.Executable action) {
        var ex = assertThrows(ModelException.class, action); assertEquals(code, ex.code()); return ex;
    }

    @Test void sendsEscapedBoundedNonStreamingJsonAndExtractsCompletionText() throws Exception {
        assertEquals("{\"ok\":true}", adapter().complete(prompt()));
        assertEquals(1, requests.get());
        assertEquals("POST", method.get());
        assertEquals("application/json", contentType.get());
        var values = new HashMap<String, String>();
        try (var parser = new JsonFactory().createParser(received.get())) {
            while (parser.nextToken() != null) {
                if (parser.currentToken() == JsonToken.FIELD_NAME) {
                    String name = parser.currentName();
                    var token = parser.nextToken();
                    if (token.isScalarValue()) values.put(name, parser.getValueAsString());
                }
            }
        }
        assertEquals(MODEL, values.get("model"));
        assertEquals(prompt().content(), values.get("prompt"));
        assertEquals("false", values.get("stream"));
        assertEquals("json", values.get("format"));
        assertEquals("0", values.get("temperature"));
        assertEquals("2048", values.get("num_predict"));
        assertTrue(values.get("system").contains("test-v1"));
        assertTrue(values.get("system").contains("untrusted"));
        assertEquals(MODEL, adapter().modelVersion());
    }

    @ParameterizedTest @CsvSource({"404,MODEL_NOT_FOUND", "429,TRANSIENT_FAILURE", "500,TRANSIENT_FAILURE",
            "502,TRANSIENT_FAILURE", "503,TRANSIENT_FAILURE", "504,TRANSIENT_FAILURE",
            "400,HTTP_ERROR", "401,AUTHENTICATION", "403,AUTHENTICATION", "302,HTTP_ERROR"})
    void classifiesStatusWithoutEchoingErrorBodyOrRetrying(int status, String code) {
        handler = exchange -> {
            exchange.getResponseHeaders().set("Location", base + "/api/generate");
            reply(exchange, status, "{\"error\":\"private-provider-marker\"}", "application/json");
        };
        var ex = fails(ModelException.Code.valueOf(code), () -> adapter().complete(prompt()));
        assertFalse(ex.getMessage().contains("private-provider-marker"));
        assertNull(ex.getCause());
        assertEquals(1, requests.get());
    }

    @ParameterizedTest @ValueSource(strings={"", "{}", "[]", "not-json",
            "{\"model\":\"qwen2.5:7b\",\"response\":\"ok\"}",
            "{\"model\":\"qwen2.5:7b\",\"response\":3,\"done\":true}",
            "{\"model\":\"qwen2.5:7b\",\"response\":\"ok\",\"done\":false}",
            "{\"model\":\"qwen2.5:7b\",\"response\":\"ok\",\"done\":\"true\"}",
            "{\"model\":\"another-model\",\"response\":\"ok\",\"done\":true}",
            "{\"response\":\"ok\",\"done\":true}",
            "{\"model\":null,\"response\":\"ok\",\"done\":true}",
            "{\"model\":\"qwen2.5:7b\",\"response\":\" \",\"done\":true}",
            "{\"model\":\"qwen2.5:7b\",\"response\":\"ok\",\"done\":true,\"done_reason\":\"length\"}",
            "{\"model\":\"qwen2.5:7b\",\"response\":\"one\",\"response\":\"two\",\"done\":true}",
            "{\"model\":\"qwen2.5:7b\",\"response\":\"ok\",\"done\":true,\"error\":\"private-marker\"}",
            "{\"model\":\"qwen2.5:7b\",\"response\":\"ok\",\"done\":true} {}"})
    void malformedIncompleteOrMismatchedEnvelopesFailSafely(String body) {
        handler = exchange -> reply(exchange, 200, body, "application/json");
        var ex = fails(INVALID_RESPONSE, () -> adapter().complete(prompt()));
        assertFalse(ex.getMessage().contains("private-marker"));
    }

    @Test void refusesStreamingAndWrongContentTypes() {
        handler = exchange -> reply(exchange, 200, envelope("ok"), "application/x-ndjson");
        fails(INVALID_RESPONSE, () -> adapter().complete(prompt()));
        handler = exchange -> reply(exchange, 200, envelope("ok"), "text/html");
        fails(INVALID_RESPONSE, () -> adapter().complete(prompt()));
    }

    @Test void capsBodyDuringAccumulationAndRejectsInvalidUtf8() {
        handler = exchange -> reply(exchange, 200, envelope("a".repeat(4096)), "application/json");
        fails(RESPONSE_TOO_LARGE, () -> new OllamaAdapter(base, MODEL, Duration.ofSeconds(2), 4096, 128).complete(prompt()));
        handler = exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, 2);
            try (var out = exchange.getResponseBody()) { out.write(new byte[]{(byte) 0xc3, (byte) 0x28}); }
        };
        fails(INVALID_RESPONSE, () -> adapter().complete(prompt()));
    }

    @Test void invalidAndOversizedRequestsNeverReachServer() {
        var tiny = new OllamaAdapter(base, MODEL, Duration.ofSeconds(2), 512, 4096);
        fails(INVALID_REQUEST, () -> tiny.complete(new Prompt("test-v1", "planning", "x".repeat(2000))));
        fails(INVALID_REQUEST, () -> tiny.complete(new Prompt("test-v1", "planning", "\u0001".repeat(100))));
        fails(INVALID_REQUEST, () -> tiny.complete(null));
        assertEquals(0, requests.get());
    }

    @Test void deadlineCoversWaitingForHeaders() {
        var release = new CountDownLatch(1);
        handler = exchange -> {
            try { release.await(); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            try { reply(exchange, 200, envelope("late"), "application/json"); } catch (IOException ignored) { }
        };
        try {
            fails(TIMEOUT, () -> new OllamaAdapter(base, MODEL, Duration.ofMillis(500), 4096, 4096).complete(prompt()));
        } finally { release.countDown(); }
    }

    @Test void deadlineAlsoCoversStalledBodyAfterHeaders() {
        var release = new CountDownLatch(1);
        handler = exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, 0);
            try (var out = exchange.getResponseBody()) {
                out.write('{'); out.flush();
                try { release.await(); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            } catch (IOException ignored) { }
        };
        try {
            fails(TIMEOUT, () -> new OllamaAdapter(base, MODEL, Duration.ofMillis(500), 4096, 4096).complete(prompt()));
        } finally { release.countDown(); }
    }

    @Test void interruptionBeforeDispatchPreservesFlagAndSpendsNoRequest() {
        Thread.currentThread().interrupt();
        try {
            fails(INTERRUPTED, () -> adapter().complete(prompt()));
            assertTrue(Thread.currentThread().isInterrupted());
            assertEquals(0, requests.get());
        } finally { Thread.interrupted(); }
    }

    @Test void interruptionDuringRequestCancelsAndPreservesCallerFlag() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var finished = new CountDownLatch(1);
        var code = new AtomicReference<ModelException.Code>();
        var interrupted = new AtomicBoolean();
        handler = exchange -> {
            entered.countDown();
            try { release.await(); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            try { reply(exchange, 200, envelope("late"), "application/json"); } catch (IOException ignored) { }
        };
        var adapter = adapter();
        Thread caller = new Thread(() -> {
            try { adapter.complete(prompt()); }
            catch (ModelException ex) { code.set(ex.code()); interrupted.set(Thread.currentThread().isInterrupted()); }
            finally { finished.countDown(); }
        });
        caller.setDaemon(true); caller.start();
        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            caller.interrupt();
            assertTrue(finished.await(2, TimeUnit.SECONDS));
            assertEquals(INTERRUPTED, code.get());
            assertTrue(interrupted.get());
        } finally { release.countDown(); caller.interrupt(); }
    }

    @Test void unavailableLocalPortHasTypedFailure() throws Exception {
        int port;
        try (var socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
        fails(UNAVAILABLE, () -> new OllamaAdapter(URI.create("http://127.0.0.1:" + port), MODEL,
                Duration.ofSeconds(2), 4096, 4096).complete(prompt()));
    }

    @Test void constructorAndPromptContractsRejectInvalidConfiguration() {
        for (String endpoint : List.of("relative", "file:///tmp", "http://user:private@localhost:11434",
                "http://localhost:11434/?x=1", "http://localhost:11434/#fragment", "http://localhost:0")) {
            assertThrows(IllegalArgumentException.class, () -> new OllamaAdapter(URI.create(endpoint), MODEL));
        }
        assertThrows(IllegalArgumentException.class, () -> new OllamaAdapter(base, "bad model"));
        assertThrows(IllegalArgumentException.class, () -> new OllamaAdapter(base, MODEL, Duration.ZERO, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new OllamaAdapter(base, MODEL, Duration.ofSeconds(61), 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new OllamaAdapter(base, MODEL, Duration.ofSeconds(1), 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new Prompt("bad version", "planning", "text"));
        assertThrows(IllegalArgumentException.class, () -> new Prompt("v1", "planning", " "));
    }

    @Test void modelBudgetIsSharedAtomicAndCannotExceedSixCalls() throws Exception {
        var budget = new ModelBudget();
        var callers = Executors.newFixedThreadPool(8);
        var accepted = new AtomicInteger();
        try {
            var tasks = new ArrayList<Callable<Void>>();
            for (int i = 0; i < 20; i++) tasks.add(() -> {
                try { budget.acquire(); accepted.incrementAndGet(); }
                catch (ModelException ex) { assertEquals(BUDGET_EXHAUSTED, ex.code()); }
                return null;
            });
            for (var result : callers.invokeAll(tasks)) result.get();
            assertEquals(6, accepted.get());
            assertEquals(6, budget.used());
            assertEquals(0, budget.remaining());
            fails(BUDGET_EXHAUSTED, budget::acquire);
        } finally { callers.shutdownNow(); }
        assertThrows(IllegalArgumentException.class, () -> new ModelBudget(0));
        assertThrows(IllegalArgumentException.class, () -> new ModelBudget(7));
    }
}
