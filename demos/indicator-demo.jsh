import sentinel.data.*;
import sentinel.domain.*;
import sentinel.tools.*;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
// Fixed review time keeps this synthetic walkthrough reproducible.
var store = new DatasetStore("demo", Clock.fixed(Instant.parse("2026-10-27T12:00:00Z"), ZoneOffset.UTC), Duration.ofDays(30));
System.out.println("Loaded: " + store.loadIndicatorFeed(Path.of("data/indicators/threat-feed.json")));
var command = new IndicatorLookupCommand(store);
var fresh = command.execute(new ToolCall(UUID.randomUUID(), "indicatorLookup", Map.of("indicator", "192.0.2.10", "feedVersion", "demo-feed-v1")));
System.out.println("Match: " + fresh.status() + " / " + fresh.indicatorMatch());
System.out.println("Evidence: " + fresh.evidence().get(0).provenance());
var stale = command.execute(new ToolCall(UUID.randomUUID(), "indicatorLookup", Map.of("indicator", " OLD.EXAMPLE. ", "feedVersion", "demo-feed-v1")));
System.out.println("Old match: " + stale.status() + " / " + stale.indicatorMatch());
var unknown = command.execute(new ToolCall(UUID.randomUUID(), "indicatorLookup", Map.of("indicator", "missing.example", "feedVersion", "demo-feed-v1")));
System.out.println("No match: " + unknown.status() + " / verdict=" + unknown.indicatorMatch().verdict() + " / evidence=" + unknown.evidence().size());
try { command.execute(new ToolCall(UUID.randomUUID(), "indicatorLookup", Map.of("indicator", "https://bad.example/path", "feedVersion", "demo-feed-v1"))); } catch (ToolException ex) { System.out.println("Invalid indicator: " + ex.code()); }
try { new IndicatorLookupCommand(new DatasetStore()).execute(new ToolCall(UUID.randomUUID(), "indicatorLookup", Map.of("indicator", "192.0.2.10", "feedVersion", "demo-feed-v1"))); } catch (ToolException ex) { System.out.println("No loaded feed: " + ex.code()); }
System.out.println("Event dataset unchanged: " + store.currentVersion() + " / " + store.events("v0").size());
/exit
