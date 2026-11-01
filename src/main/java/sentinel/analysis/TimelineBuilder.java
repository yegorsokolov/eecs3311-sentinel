package sentinel.analysis;
import java.util.*;
import java.time.*;
import sentinel.domain.*;
import sentinel.data.*;
import sentinel.persistence.JsonCodec;
/** Stable UTC ordering; related pairs are candidates, never causal assertions. */
public final class TimelineBuilder {
    public record Relationship(String firstEventId,String secondEventId,List<String> sharedFields) {public Relationship{sharedFields=List.copyOf(sharedFields);}}
    public List<Event> build(List<Evidence> evidence) {
        var unique=new HashMap<String,Event>();
        for(var e:evidence){if(!((Map<?,?>)JsonCodec.tree(e.payload())).containsKey("eventId"))continue;
            Event event=JsonCodec.decode(e.payload(),Event.class);if(!event.eventId().equals(e.sourceEventId()) || !event.sourcePosition().equals(e.provenance()))throw new IllegalArgumentException("Evidence provenance conflict.");
            Event prev=unique.putIfAbsent(event.eventId(),event);if(prev!=null && !prev.equals(event))throw new IllegalArgumentException("Conflicting source event ID.");}
        return unique.values().stream().sorted(Comparator.comparing(Event::timestamp).thenComparing(Event::eventId)).toList();
    }
    public List<Event> build(DatasetStore store,CaseSnapshot snapshot){return CaseEvidence.events(store,snapshot);}
    public List<Relationship> correlate(List<Event> timeline) {
        var ordered=timeline.stream().sorted(Comparator.comparing(Event::timestamp).thenComparing(Event::eventId)).toList();var result=new ArrayList<Relationship>();
        for(int i=0;i<ordered.size();i++)for(int j=i+1;j<ordered.size();j++){
            var a=ordered.get(i);var b=ordered.get(j);if(Duration.between(a.timestamp(),b.timestamp()).compareTo(Duration.ofMinutes(15))>0)break;
            var fields=new ArrayList<String>();if(a.accountId()!=null && a.accountId().equals(b.accountId()))fields.add("accountId");if(a.hostId()!=null && a.hostId().equals(b.hostId()))fields.add("hostId");
            if(!fields.isEmpty())result.add(new Relationship(a.eventId(),b.eventId(),fields));if(result.size()>=1000)return List.copyOf(result);
        }return List.copyOf(result);
    }
}
