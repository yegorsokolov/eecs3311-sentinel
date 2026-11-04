package sentinel.llm;

import com.sun.net.httpserver.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import sentinel.config.ModelSettings;
import sentinel.domain.Prompt;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static sentinel.llm.ModelException.Code.*;

class OpenAICompatibleAdapterTest {
    HttpServer server;
    ExecutorService pool;
    URI base;
    volatile HttpHandler handler;
    AtomicReference<String> body = new AtomicReference<>(), auth = new AtomicReference<>();
    AtomicInteger calls = new AtomicInteger();
    static final Prompt PROMPT = new Prompt("v1", "planning", "Return JSON for a \"quoted\" goal.");
    static final String OK = "{\"model\":\"resolved-alias\",\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"{\\\"ok\\\":true}\"},\"finish_reason\":\"stop\"}]}";
    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        pool = Executors.newCachedThreadPool(); server.setExecutor(pool);
        handler = exchange -> reply(exchange, 200, OK);
        server.createContext("/v1/chat/completions", exchange -> {
            calls.incrementAndGet(); body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            auth.set(exchange.getRequestHeaders().getFirst("Authorization")); handler.handle(exchange);
        });
        server.start(); base = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/");
    }
    @AfterEach void stop() { server.stop(0); pool.shutdownNow(); }
    OpenAICompatibleAdapter adapter(boolean json, String field, String key, Duration timeout) {
        return new OpenAICompatibleAdapter(new ModelSettings("openai-compatible", base, "qwen", key, json, field), timeout);
    }
    OpenAICompatibleAdapter adapter() { return adapter(true, "max_tokens", "test-secret", Duration.ofSeconds(2)); }
    void reply(HttpExchange e, int status, String value) throws java.io.IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        e.getResponseHeaders().set("Content-Type", "application/json");
        e.sendResponseHeaders(status, bytes.length);
        try (var out = e.getResponseBody()) { out.write(bytes); }
    }
    @Test void sendsBearerAndMessagesAndAcceptsResolvedModelAlias() {
        assertEquals("{\"ok\":true}", adapter().complete(PROMPT));
        assertEquals("Bearer test-secret", auth.get());
        assertTrue(body.get().contains("\"messages\""));
        assertTrue(body.get().contains("\\\"quoted\\\""));
        assertTrue(body.get().contains("\"max_tokens\":2048"));
        assertTrue(body.get().contains("\"type\":\"json_object\""));
        assertFalse(body.get().contains("test-secret"));
        assertEquals(1, calls.get());
    }
    @Test void optionalJsonModeAndAlternativeTokenFieldAndNoKey() {
        adapter(false, "max_completion_tokens", "", Duration.ofSeconds(2)).complete(PROMPT);
        assertNull(auth.get()); assertFalse(body.get().contains("response_format"));
        assertTrue(body.get().contains("\"max_completion_tokens\":2048"));
    }
    @ParameterizedTest @ValueSource(ints={401,403,429,503,404,400,302})
    void errorsAreTypedSanitizedAndNeverRetried(int status) {
        handler = e -> { e.getResponseHeaders().set("Location", base.toString()); reply(e, status, "private-provider-body test-secret"); };
        var ex = assertThrows(ModelException.class, () -> adapter().complete(PROMPT));
        var expected = status == 401 || status == 403 ? AUTHENTICATION : status == 429 || status == 503
                ? TRANSIENT_FAILURE : status == 404 ? MODEL_NOT_FOUND : HTTP_ERROR;
        assertEquals(expected, ex.code()); assertEquals(1, calls.get());
        assertFalse(ex.getMessage().contains("test-secret")); assertFalse(ex.getMessage().contains("private-provider"));
    }
    @ParameterizedTest @ValueSource(strings={"{}", "{", "{\"choices\":[]}", "{\"error\":\"private\"}",
            "{\"choices\":[{\"message\":{\"content\":\"ok\"},\"finish_reason\":\"length\"}]}",
            "{\"choices\":[{\"message\":{\"content\":\"ok\",\"refusal\":\"no\"},\"finish_reason\":\"stop\"}]}",
            "{\"choices\":[{\"message\":{\"content\":\"one\",\"content\":\"two\"},\"finish_reason\":\"stop\"}]}",
            "{\"choices\":[{\"message\":{\"content\":\"\"},\"finish_reason\":\"stop\"}]}"})
    void rejectsMalformedOrIncompleteEnvelopes(String response) {
        handler = e -> reply(e, 200, response);
        assertEquals(INVALID_RESPONSE, assertThrows(ModelException.class, () -> adapter().complete(PROMPT)).code());
    }
    @Test void rejectsTrailingJsonAndMultipleChoices() {
        for (String response : java.util.List.of(OK + " {}", OK.replace("}]}", "},{}]}"))) {
            handler = e -> reply(e, 200, response);
            assertEquals(INVALID_RESPONSE, assertThrows(ModelException.class, () -> adapter().complete(PROMPT)).code());
        }
    }
    @Test void responseBoundAndRequestBound() {
        handler = e -> reply(e, 200, "x".repeat(OllamaAdapter.MAX_RESPONSE_BYTES + 1));
        assertEquals(RESPONSE_TOO_LARGE, assertThrows(ModelException.class, () -> adapter().complete(PROMPT)).code());
        assertEquals(INVALID_REQUEST, assertThrows(ModelException.class, () -> adapter().complete(
                new Prompt("v1", "planning", "x".repeat(OllamaAdapter.MAX_REQUEST_BYTES + 1)))).code());
        assertEquals(1, calls.get());
    }
    @Test void deadlineCoversStalledBody() {
        handler = e -> {
            e.getResponseHeaders().set("Content-Type", "application/json"); e.sendResponseHeaders(200, 0);
            try (var out = e.getResponseBody()) {
                out.write('{'); out.flush();
                try { Thread.sleep(2000); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            }
        };
        assertEquals(TIMEOUT, assertThrows(ModelException.class, () -> adapter(true, "max_tokens", "", Duration.ofMillis(200)).complete(PROMPT)).code());
    }
}
