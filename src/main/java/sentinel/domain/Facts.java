package sentinel.domain;
import java.util.*;
public record Facts(Map<String,Boolean> predicates,Map<String,List<String>> supportingEventIds,List<String> limitations) {
    public Facts {predicates=Map.copyOf(predicates);var m=new HashMap<String,List<String>>();supportingEventIds.forEach((k,v)->m.put(k,List.copyOf(v)));supportingEventIds=Map.copyOf(m);limitations=List.copyOf(limitations);}
}
