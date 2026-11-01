import sentinel.application.*;
import sentinel.analysis.*;
import sentinel.config.*;
import sentinel.data.*;
import sentinel.domain.*;
import sentinel.events.*;
import sentinel.llm.*;
import sentinel.persistence.*;
import sentinel.planning.*;
import sentinel.reporting.*;
import sentinel.tools.*;
import com.fasterxml.jackson.core.*;
import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
System.out.println("FAKE HTTP FIXTURE: no Ollama model inference is performed.");
var calls = new AtomicInteger();
var unavailable = new AtomicBoolean();
var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
server.createContext("/api/generate", exchange -> {
    exchange.getRequestBody().readAllBytes();
    int index = calls.getAndIncrement();
    String account = index == 0 ? "outside-account" : "demo-analyst";
    String planJson = "{\"version\":\"planning-v1\",\"steps\":[{\"id\":\"collect\",\"tool\":\"logSearch\",\"args\":{\"datasetId\":\"demo\",\"datasetVersion\":\"v0\",\"accountId\":\"" + account + "\",\"start\":\"2026-10-22T12:00:00Z\",\"end\":\"2026-10-22T12:05:00Z\",\"limit\":\"100\"},\"dependsOn\":[]}]}";
    var output = new StringWriter();
    try (var json = new JsonFactory().createGenerator(output)) {
        json.writeStartObject();
        if (unavailable.get()) json.writeStringField("error", "Synthetic transient failure");
        else { json.writeStringField("model", "qwen2.5:7b"); json.writeStringField("response", planJson); json.writeBooleanField("done", true); }
        json.writeEndObject();
    }
    byte[] body = output.toString().getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(unavailable.get() ? 503 : 200, body.length);
    try (var stream = exchange.getResponseBody()) { stream.write(body); }
});
server.start();
var store = new DatasetStore("demo");
var tools = ToolExecutor.local(store);
var validator = new PlanValidator(tools, store);
var adapter = new OllamaAdapter(URI.create("http://127.0.0.1:" + server.getAddress().getPort()), "qwen2.5:7b");
var planner = new LLMPlanningStrategy(adapter, validator);
var bus = new EventBus();
var analysis = new AnalysisService(prompt -> { throw new UnsupportedOperationException("Analysis is outside this demo"); }, new TimelineBuilder(), new RuleSeverityPolicy());
var engine = new AgentEngine(planner, tools, analysis, bus, store);
var controller = new AgentController(engine, new JsonCaseRepository(Path.of("runtime/cases")), new LogImporter(store), new ReportService(), bus);
var scope = new CaseScope("demo", "v0", "demo-analyst", Instant.parse("2026-10-22T12:00:00Z"), Instant.parse("2026-10-22T12:05:00Z"));
var investigation = controller.start(new Request("Investigate demo analyst", scope));
System.out.println("Repaired fake model plan: " + investigation.snapshot().status() + " / " + investigation.snapshot().plan().origin() + " / HTTP calls=" + calls.get());
System.out.println("Accepted prompt version: " + investigation.snapshot().promptVersion() + " / case evidence=" + investigation.snapshot().evidence().size());
calls.set(0);
var tight = new ModelBudget(1);
try { planner.createPlan(new CaseContext(investigation.snapshot()), tight); } catch (PlanningException ex) { System.out.println("Repair budget: " + ex.code() + " / used=" + tight.used() + " / HTTP calls=" + calls.get()); }
calls.set(0);
unavailable.set(true);
var fallbackCase = controller.start(new Request("Investigate with unavailable fake provider", scope));
System.out.println("Provider failure: " + fallbackCase.snapshot().status() + " / " + fallbackCase.snapshot().history().get(1).outcome() + " / HTTP calls=" + calls.get());
int beforeApproval = calls.get();
controller.acceptRulePlan(fallbackCase.snapshot().id());
System.out.println("Explicitly approved fallback: " + fallbackCase.snapshot().plan().origin() + " / " + fallbackCase.snapshot().status() + " / additional HTTP calls=" + (calls.get() - beforeApproval));
System.out.println("Dataset unchanged: " + store.currentVersion() + " / events=" + store.events("v0").size());
tools.close();
server.stop(0);
/exit
