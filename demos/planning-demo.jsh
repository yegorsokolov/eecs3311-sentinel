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
var validator = new PlanValidator(tools, store);
var rules = new RulePlanningStrategy(validator, store);
var bus = new EventBus();
// Analysis and durable storage are not invoked in this component walkthrough.
var analysis = new AnalysisService(prompt -> { throw new UnsupportedOperationException("No model calls in this demo"); }, new TimelineBuilder(), new RuleSeverityPolicy());
var engine = new AgentEngine(rules, tools, analysis, bus, store);
var controller = new AgentController(engine, new JsonCaseRepository(Path.of("runtime/cases")), new LogImporter(store), new ReportService(), bus);
var investigation = controller.start(new Request("Investigate demo analyst", null));
var id = investigation.snapshot().id();
System.out.println("Missing scope: " + investigation.snapshot().status() + " / " + investigation.snapshot().history().get(1).outcome());
controller.clarifyScope(id, new CaseScope("demo", "v0", "demo-analyst", Instant.parse("2026-10-22T12:00:00Z"), Instant.parse("2026-10-22T12:05:00Z")));
System.out.println("Before approval: " + investigation.snapshot().status());
var plan = controller.acceptRulePlan(id);
System.out.println("Approved plan: " + plan.origin() + " / " + investigation.snapshot().status() + " / tools=" + plan.steps().stream().map(ToolCall::toolName).toList());
System.out.println("Initial next step: " + plan.next(Set.of()).orElseThrow().toolName());
System.out.println("After search: " + plan.next(Set.of(plan.steps().get(0).id())).orElseThrow().toolName());
var wrong = new HashMap<String,String>(plan.steps().get(0).args());
wrong.put("accountId", "other-account");
var bad = new Plan(UUID.randomUUID(), 1, List.of(new ToolCall(UUID.randomUUID(), "logSearch", wrong)), Map.of(), Plan.Origin.MODEL);
try { validator.validate(bad, new CaseContext(investigation.snapshot())); } catch (PlanningException ex) { System.out.println("Out-of-scope draft: " + ex.code()); }
var forward = new Plan(UUID.randomUUID(), 1, plan.steps(), Map.of(plan.steps().get(0).id(), List.of(plan.steps().get(1).id())), Plan.Origin.MODEL);
try { validator.validate(forward, new CaseContext(investigation.snapshot())); } catch (PlanningException ex) { System.out.println("Invalid dependency: " + ex.code()); }
controller.acceptRulePlan(id);
controller.acceptRulePlan(id);
var over = controller.acceptRulePlan(id);
System.out.println("Revision ceiling: accepted=" + (over != null) + " / last revision=" + investigation.snapshot().plan().revision());
System.out.println("Planning performed no retrieval: version=" + store.currentVersion() + " / events=" + store.events("v0").size() + " / case evidence=" + investigation.snapshot().evidence().size());
tools.close();
/exit
