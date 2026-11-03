package sentinel.analysis;
import java.util.*;
import java.time.*;
import sentinel.domain.*;
import sentinel.data.*;
/** Stage 1 policy v1: each R1-R4 contributes once, independent of model output. */
public final class RuleSeverityPolicy implements SeverityPolicy {
    public Severity assess(Facts facts){var limitations=new ArrayList<>(facts.limitations());if(!facts.predicates().getOrDefault("AUTH",false))return new Severity(0,SeverityLevel.UNDETERMINED,List.of(),"1",append(limitations,"No usable authentication evidence"));
        int score=0;var ids=new ArrayList<String>();int[] weights={2,3,2,1};for(int i=0;i<4;i++)if(facts.predicates().getOrDefault("R"+(i+1),false)){score+=weights[i];ids.add("R"+(i+1));}
        return new Severity(score,score<=2?SeverityLevel.LOW:score<=5?SeverityLevel.MEDIUM:SeverityLevel.HIGH,ids,"1",limitations);}
    private static List<String> append(List<String> l,String s){l.add(s);return l;}
    public static boolean login(Event e){return Set.of("login","authentication","auth").contains(e.eventType().toLowerCase(Locale.ROOT));}
    public static Facts derive(DatasetStore store,CaseSnapshot snapshot,Set<String> criticalHosts,boolean inventorySupplied,boolean partial){
        var allEvents=CaseEvidence.events(store,snapshot);var auth=allEvents.stream().filter(RuleSeverityPolicy::login).filter(e->e.accountId()!=null && Set.of("success","failure").contains(e.outcome()==null?"":e.outcome().toLowerCase(Locale.ROOT))).toList();
        var support=new HashMap<String,List<String>>();var p=new HashMap<String,Boolean>();p.put("AUTH",!auth.isEmpty());
        var bursts=new ArrayList<Event>();var failures=auth.stream().filter(e->"failure".equalsIgnoreCase(e.outcome())).toList();
        for(int i=0;i<failures.size();i++){Event end=failures.get(i);var group=failures.subList(0,i+1).stream().filter(e->e.accountId().equals(end.accountId()) && !e.timestamp().isBefore(end.timestamp().minusSeconds(600))).toList();if(group.size()>=5){bursts.add(end);support.putIfAbsent("R1",group.stream().map(Event::eventId).toList());}}
        p.put("R1",!bursts.isEmpty());
        var successes=auth.stream().filter(e->"success".equalsIgnoreCase(e.outcome())).filter(e->bursts.stream().anyMatch(b->b.accountId().equals(e.accountId()) && e.timestamp().isAfter(b.timestamp()) && !e.timestamp().isAfter(b.timestamp().plusSeconds(600)))).toList();
        p.put("R2",!successes.isEmpty());if(!successes.isEmpty()){var ids=new ArrayList<>(support.get("R1"));ids.addAll(successes.stream().map(Event::eventId).toList());support.put("R2",ids.stream().distinct().toList());}
        var malicious=new HashSet<String>();for(var item:snapshot.evidence())store.resolveIndicator(item).filter(r->Objects.equals(r.feedVersion(),snapshot.feedVersion()) && r.verdict().equals("MALICIOUS")).ifPresent(r->malicious.add(r.indicator()));
        var bad=auth.stream().filter(e->{try{return malicious.contains(Indicator.normalize(e.sourceIp()));}catch(IllegalArgumentException x){return false;}}).map(Event::eventId).toList();p.put("R3",!bad.isEmpty());support.put("R3",bad);
        var critical=allEvents.stream().filter(e->e.hostId()!=null && criticalHosts.contains(e.hostId())).map(Event::eventId).toList();p.put("R4",!critical.isEmpty());support.put("R4",critical);
        var limits=new ArrayList<String>();if(!inventorySupplied)limits.add("Asset criticality unknown; R4 contributes zero");if(snapshot.feedVersion()==null)limits.add("No indicator feed selected; R3 contributes zero");if(partial)limits.add("Partial evidence coverage; absence is not safety");
        return new Facts(p,support,limits);
    }
}
