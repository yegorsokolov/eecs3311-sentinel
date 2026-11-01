package sentinel.analysis;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import sentinel.data.DatasetStore;
import sentinel.domain.*;
import sentinel.persistence.JsonCodec;
import static org.junit.jupiter.api.Assertions.*;

class TimelineBuilderTest {
    private final TimelineBuilder builder = new TimelineBuilder();
    private final Instant start = Instant.parse("2026-10-22T12:00:00Z");
    private Event event(String id, long seconds, String account, String host) {
        return new Event(id, start.plusSeconds(seconds), "login", account, null, host,
                "success", "fixture#/" + id, "v1");
    }
    private Evidence evidence(Event event) {
        return new Evidence(UUID.randomUUID(), event.eventId(), "v1", event.sourcePosition(), JsonCodec.encode(event));
    }
    @Test void deduplicatesSourceEventsAndOrdersTimestampTiesById() {
        Event a=event("a",0,"alice","h"), b=event("b",0,"alice","h"), c=event("c",1,"alice","h");
        var result=builder.build(List.of(evidence(c),evidence(b),evidence(a),evidence(b)));
        assertEquals(List.of(a,b,c),result);
        assertThrows(UnsupportedOperationException.class,()->result.clear());
    }
    @Test void rejectsConflictingContentUnderOneSourceId() {
        assertThrows(IllegalArgumentException.class,()->builder.build(List.of(
                evidence(event("a",0,"alice","h")),evidence(event("a",1,"alice","h")))));
    }
    @Test void rejectsMismatchedSourceIdentityAndProvenance() {
        Event a=event("a",0,"alice","h");
        assertThrows(IllegalArgumentException.class,()->builder.build(List.of(
                new Evidence(UUID.randomUUID(),"other","v1",a.sourcePosition(),JsonCodec.encode(a)))));
        assertThrows(IllegalArgumentException.class,()->builder.build(List.of(
                new Evidence(UUID.randomUUID(),"a","v1","forged",JsonCodec.encode(a)))));
    }
    @Test void skipsNonEventEvidenceAndRejectsMalformedEventPayloads() {
        var indicator=new Evidence(UUID.randomUUID(),"indicator","v1","feed#/0","{\"indicator\":\"192.0.2.1\"}");
        assertTrue(builder.build(List.of(indicator)).isEmpty());
        assertThrows(IllegalArgumentException.class,()->builder.build(List.of(
                new Evidence(UUID.randomUUID(),"a","v1","fixture#/a","{\"eventId\":\"a\"}"))));
        assertThrows(IllegalArgumentException.class,()->builder.build(List.of(
                new Evidence(UUID.randomUUID(),"a","v1","fixture#/a","{\"eventId\":\"a\",\"eventId\":\"b\"}"))));
    }
    @Test void correlationIncludesExactWindowAndExcludesNextSecond() {
        Event a=event("a",0,"alice",null),b=event("b",900,"alice",null),c=event("c",901,"alice",null);
        var pairs=builder.correlate(List.of(c,b,a));
        assertEquals(List.of(new TimelineBuilder.Relationship("a","b",List.of("accountId")),
                new TimelineBuilder.Relationship("b","c",List.of("accountId"))),pairs);
    }
    @Test void sharedAccountAndHostAreReportedWithoutInferringMissingValues() {
        Event a=event("a",0,"alice","host"),b=event("b",1,"alice","host"),c=event("c",2,null,null);
        var pairs=builder.correlate(List.of(c,b,a));
        assertEquals(List.of(new TimelineBuilder.Relationship("a","b",List.of("accountId","hostId"))),pairs);
        assertThrows(UnsupportedOperationException.class,()->pairs.get(0).sharedFields().clear());
    }
    @Test void sharedHostCorrelatesDifferentAccountsButUnrelatedEventsDoNot() {
        var pairs=builder.correlate(List.of(event("a",0,"alice","host"),event("b",1,"bob","host"),event("c",2,"carol","other")));
        assertEquals(List.of(new TimelineBuilder.Relationship("a","b",List.of("hostId"))),pairs);
    }
    @Test void relationshipOutputIsDeterministicImmutableAndCapped() {
        var events=new ArrayList<Event>();for(int n=0;n<50;n++)events.add(event(String.format("e%02d",n),n,"alice","host"));
        var first=builder.correlate(events);Collections.reverse(events);
        assertEquals(1000,first.size());assertEquals(first,builder.correlate(events));
        assertThrows(UnsupportedOperationException.class,()->first.clear());
    }
    @Test void canonicalBuildUsesOnlyAcceptedCurrentScopeEvidence() {
        var store=new DatasetStore("demo");
        store.ingest(new ImportBatch(List.of(event("inside",0,"alice","h"),event("other",0,"bob","h"),event("late",61,"alice","h"))));
        var scope=new CaseScope("demo",store.currentVersion(),"alice",start,start.plusSeconds(60));
        var investigation=new Investigation(new Request("Investigate alice",scope));
        assertTrue(builder.build(store,investigation.snapshot()).isEmpty());
        var args=Map.of("datasetId","demo","datasetVersion",store.currentVersion(),"start",start.toString(),"end",start.plusSeconds(120).toString(),"limit","100");
        investigation.addEvidence(store.search(new ToolCall(UUID.randomUUID(),"logSearch",args)));
        assertEquals(List.of("inside"),builder.build(store,investigation.snapshot()).stream().map(Event::eventId).toList());
        var wrongStore=new DatasetStore("other");assertTrue(builder.build(wrongStore,investigation.snapshot()).isEmpty());
    }
    @Test void emptyEvidenceAndEmptyTimelineProduceNoEventsOrRelationships() {
        assertTrue(builder.build(List.of()).isEmpty());assertTrue(builder.correlate(List.of()).isEmpty());
    }
}
