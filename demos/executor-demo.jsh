import sentinel.data.*;
import sentinel.domain.*;
import sentinel.tools.*;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
var store = new DatasetStore("demo", Clock.fixed(Instant.parse("2026-10-28T12:00:00Z"), ZoneOffset.UTC), Duration.ofDays(30));
new LogImporter(store).importFile(Path.of("data/samples/import-valid.json"));
store.loadIndicatorFeed(Path.of("data/indicators/threat-feed.json"));
var bounds = new CaseScope("demo", "v1", "demo-analyst", Instant.parse("2026-10-22T12:00:00Z"), Instant.parse("2026-10-22T12:05:00Z"));
// Trusted fixture context only; automatic case-evidence attachment comes in the next increment.
var executor = ToolExecutor.local(store);
var scope = ToolScope.account(bounds, "demo-feed-v1", Set.of());
var session = executor.openSession(scope);
var args = new HashMap<String,String>(Map.of("datasetId", "demo", "datasetVersion", "v1", "accountId", "demo-analyst", "start", bounds.start().toString(), "end", bounds.end().toString(), "limit", "10"));
var full = session.execute(new ToolCall(UUID.randomUUID(), "logSearch", args));
System.out.println("Scoped search: " + full.status() + " / events=" + full.evidence().size());
args.put("limit", "0010");
args.put("start", "2026-10-22T08:00:00-04:00");
try { session.execute(new ToolCall(UUID.randomUUID(), "logSearch", args)); } catch (ToolException ex) { System.out.println("Equivalent call: " + ex.code()); }
args.put("accountId", "other-account");
try { session.execute(new ToolCall(UUID.randomUUID(), "logSearch", args)); } catch (ToolException ex) { System.out.println("Outside target: " + ex.code()); }
args.put("accountId", "demo-analyst");
args.put("limit", "1");
var activity = session.execute(new ToolCall(UUID.randomUUID(), "userActivity", args));
System.out.println("Activity: " + activity.status() + " / total=" + activity.activitySummary().totalEvents() + " / returned=" + activity.activitySummary().returnedEvents());
var returnedIds = full.evidence().stream().map(Evidence::sourceEventId).collect(Collectors.toSet());
var observed = store.events("v1").stream().filter(e -> returnedIds.contains(e.eventId())).map(Event::sourceIp).filter(Objects::nonNull).collect(Collectors.toSet());
session.acceptEvidenceIndicators(observed);
var indicator = session.execute(new ToolCall(UUID.randomUUID(), "indicatorLookup", Map.of("indicator", "192.0.2.10", "feedVersion", "demo-feed-v1")));
System.out.println("Observed indicator: " + indicator.status() + " / verdict=" + indicator.indicatorMatch().verdict());
System.out.println("Session budget: used=" + session.attemptsUsed() + " / remaining=" + session.attemptsRemaining());
var budget = executor.openSession(scope);
for (int limit = 1; limit <= 8; limit++) { args.put("limit", Integer.toString(limit)); budget.execute(new ToolCall(UUID.randomUUID(), "logSearch", args)); }
args.put("limit", "9");
try { budget.execute(new ToolCall(UUID.randomUUID(), "logSearch", args)); } catch (ToolException ex) { System.out.println("Ninth attempt: " + ex.code() + " / used=" + budget.attemptsUsed()); }
executor.close();
System.out.println("Dataset unchanged: " + store.currentVersion() + " / " + store.events("v1").size());
/exit
