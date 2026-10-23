import sentinel.data.*;
import sentinel.domain.*;
import sentinel.tools.*;
import java.nio.file.Path;
import java.util.*;
var store = new DatasetStore("demo");
new LogImporter(store).importFile(Path.of("data/samples/import-valid.json"));
var command = new LogSearchCommand(store);
var args = new HashMap<String,String>(Map.of("datasetId", "demo", "datasetVersion", "v1", "start", "2026-10-22T12:00:00Z", "end", "2026-10-22T12:05:00Z", "limit", "10", "accountId", "demo-analyst"));
var full = command.execute(new ToolCall(UUID.randomUUID(), "logSearch", args));
System.out.println("Full: " + full.status() + " / " + full.evidence().size() + " / truncated=" + full.truncated());
System.out.println("IDs: " + full.evidence().stream().map(Evidence::sourceEventId).toList());
System.out.println("Source: " + full.evidence().get(0).provenance());
args.put("limit", "1");
var capped = command.execute(new ToolCall(UUID.randomUUID(), "logSearch", args));
System.out.println("Capped: " + capped.status() + " / " + capped.evidence().size() + " / truncated=" + capped.truncated());
args.put("accountId", "missing");
System.out.println("No match: " + command.execute(new ToolCall(UUID.randomUUID(), "logSearch", args)).status());
args.put("limit", "0");
try { command.execute(new ToolCall(UUID.randomUUID(), "logSearch", args)); } catch (ToolException ex) { System.out.println("Invalid limit: " + ex.code()); }
System.out.println("Dataset unchanged: " + store.currentVersion() + " / " + store.events("v1").size());
/exit
