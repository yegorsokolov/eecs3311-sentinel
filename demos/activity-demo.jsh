import sentinel.data.*;
import sentinel.domain.*;
import sentinel.tools.*;
import java.nio.file.Path;
import java.util.*;
var store = new DatasetStore("demo");
new LogImporter(store).importFile(Path.of("data/samples/import-valid.json"));
var command = new UserActivityCommand(store);
var args = new HashMap<String,String>(Map.of("datasetId", "demo", "datasetVersion", "v1", "start", "2026-10-22T12:00:00Z", "end", "2026-10-22T12:05:00Z", "limit", "10", "accountId", "demo-analyst"));
var full = command.execute(new ToolCall(UUID.randomUUID(), "userActivity", args));
System.out.println("Full: " + full.status() + " / IDs=" + full.evidence().stream().map(Evidence::sourceEventId).toList());
System.out.println("Summary: " + full.activitySummary());
args.put("limit", "1");
var capped = command.execute(new ToolCall(UUID.randomUUID(), "userActivity", args));
System.out.println("Capped: " + capped.status() + " / returned=" + capped.activitySummary().returnedEvents() + " / total=" + capped.activitySummary().totalEvents());
args.put("accountId", "missing");
var empty = command.execute(new ToolCall(UUID.randomUUID(), "userActivity", args));
System.out.println("No match: " + empty.status() + " / coverage=" + empty.activitySummary());
args.put("limit", "0");
try { command.execute(new ToolCall(UUID.randomUUID(), "userActivity", args)); } catch (ToolException ex) { System.out.println("Invalid limit: " + ex.code()); }
System.out.println("Dataset unchanged: " + store.currentVersion() + " / " + store.events("v1").size());
/exit
