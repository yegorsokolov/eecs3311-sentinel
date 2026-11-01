import sentinel.analysis.*;
import sentinel.domain.*;
import sentinel.persistence.*;
import java.time.*;
import java.util.*;
var start = Instant.parse("2026-10-22T12:00:00Z");
var a = new Event("a", start, "login", "demo-analyst", null, "host-1", "failure", "fixture#/a", "v1");
var b = new Event("b", start.plusSeconds(900), "login", "demo-analyst", null, "host-1", "success", "fixture#/b", "v1");
var ea = new Evidence(UUID.randomUUID(), "a", "v1", a.sourcePosition(), JsonCodec.encode(a));
var eb = new Evidence(UUID.randomUUID(), "b", "v1", b.sourcePosition(), JsonCodec.encode(b));
var builder = new TimelineBuilder();
var timeline = builder.build(List.of(eb, ea, ea));
if (!timeline.equals(List.of(a,b))) throw new AssertionError("Ordering/deduplication failed");
var related = builder.correlate(timeline);
if (related.size()!=1 || !related.get(0).sharedFields().equals(List.of("accountId","hostId"))) throw new AssertionError("Correlation failed");
System.out.println("Timeline: " + timeline.stream().map(Event::eventId).toList());
System.out.println("Related candidates within 15 minutes: " + related);
System.out.println("Synthetic evidence only; relationships do not establish causation.");
/exit
