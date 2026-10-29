import sentinel.application.*;
import sentinel.analysis.*;
import sentinel.data.*;
import sentinel.domain.*;
import sentinel.events.*;
import sentinel.persistence.*;
import sentinel.planning.*;
import sentinel.reporting.*;
import sentinel.tools.*;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
var store = new DatasetStore("demo", Clock.fixed(Instant.parse("2026-10-29T05:15:00Z"), ZoneOffset.UTC), Duration.ofDays(30));
var tools = ToolExecutor.local(store);
PlanningStrategy rules = context -> { throw new UnsupportedOperationException("Planning is a later milestone"); };
var bus = new EventBus();
// Analysis and durable storage are not invoked in this component walkthrough.
var analysis = new AnalysisService(prompt -> { throw new UnsupportedOperationException("No model calls in this demo"); }, new TimelineBuilder(), new RuleSeverityPolicy());
var engine = new AgentEngine(rules, tools, analysis, bus, store);
var controller = new AgentController(engine, new JsonCaseRepository(Path.of("runtime/cases")), new LogImporter(store), new ReportService(), bus);
store.loadIndicatorFeed(Path.of("data/indicators/threat-feed.json"));
var scope = new CaseScope("demo", "v0", "demo-analyst", Instant.parse("2026-10-22T12:00:00Z"), Instant.parse("2026-10-22T12:05:00Z"));
var investigation = controller.start(new Request("Investigate demo analyst", scope), TargetType.ACCOUNT, "demo-feed-v1");
var id = investigation.snapshot().id();
var summary = controller.importLogs(id, Path.of("data/samples/import-valid.json"));
System.out.println("Import: " + summary.datasetVersion() + " / accepted=" + summary.accepted() + " / duplicates=" + summary.duplicates() + " / case=" + investigation.snapshot().scope().datasetVersion());
var args = Map.of("datasetId", "demo", "datasetVersion", "v1", "accountId", "demo-analyst", "start", scope.start().toString(), "end", scope.end().toString(), "limit", "10");
var call = new ToolCall(UUID.randomUUID(), "logSearch", args);
var session = engine.beginQuerySession(investigation);
engine.query(investigation, call, session);
System.out.println("Attached search evidence: " + investigation.snapshot().evidence().size());
var enriched = engine.query(investigation, new ToolCall(UUID.randomUUID(), "indicatorLookup", Map.of("indicator", "192.0.2.10", "feedVersion", "demo-feed-v1")), session);
System.out.println("Enrichment: " + enriched.indicatorMatch().verdict() + " / evidence=" + investigation.snapshot().evidence().size() + " / same-session attempts=" + session.attemptsUsed());
controller.execute(id, new ToolCall(UUID.randomUUID(), "userActivity", args));
System.out.println("Evidence after overlapping activity: " + investigation.snapshot().evidence().size());
// A fixture plan exercises import invalidation without implementing Step 10 planning.
investigation.setPlan(new Plan(UUID.randomUUID(), 0, List.of(call)));
System.out.println("Fixture plan: " + investigation.snapshot().status());
var duplicate = controller.importLogs(id, Path.of("data/samples/import-valid.json"));
System.out.println("Duplicate import: accepted=" + duplicate.accepted() + " / duplicates=" + duplicate.duplicates() + " / " + investigation.snapshot().status());
controller.importLogs(id, Path.of("data/samples/import-additional.json"));
System.out.println("New version: " + investigation.snapshot().scope().datasetVersion() + " / " + investigation.snapshot().status() + " / plan=" + investigation.snapshot().plan() + " / retained evidence=" + investigation.snapshot().evidence().size());
try { engine.query(investigation, call, session); } catch (ToolException ex) { System.out.println("Old session: " + ex.code()); }
var rejected = controller.importLogs(id, Path.of("data/samples/import-invalid.json"));
System.out.println("Invalid import rejected: " + !rejected.successful() + " / version=" + investigation.snapshot().scope().datasetVersion());
System.out.println("Audit contains successful retrieval: " + investigation.snapshot().history().stream().anyMatch(entry -> entry.action().equals("TOOL_SUCCEEDED") && !entry.evidenceIds().isEmpty()));
tools.close();
/exit
