package sentinel.application;
import java.util.*;
import java.time.*;
import java.nio.file.*;
import sentinel.domain.*;
import sentinel.persistence.JsonCodec;
import sentinel.reporting.ReportService;
/** Shared action vocabulary for both views; successful mutations save through the controller. */
public final class ActionDispatcher {
    private final AgentController c;
    public ActionDispatcher(AgentController c){this.c=Objects.requireNonNull(c);}
    @SuppressWarnings("unchecked")
    public Result submit(Action action){
        try { return dispatch(action); } catch(RuntimeException ex) {
            try { c.save(c.current().id()); } catch(RuntimeException saveFailure) { ex.addSuppressed(saveFailure); }
            throw ex;
        }
    }
    private Result dispatch(Action action){
        var a=action.arguments();Object result;
        switch(action.name()){
            case "new" -> {CaseScope scope=a.containsKey("target")&&a.containsKey("start")&&a.containsKey("end")?new CaseScope(a.getOrDefault("dataset","local"),a.getOrDefault("version","v0"),a.get("target"),Instant.parse(a.get("start")),Instant.parse(a.get("end"))):null;
                result=c.start(new Request(required(a,"request"),scope),TargetType.valueOf(a.getOrDefault("type","ACCOUNT").toUpperCase(Locale.ROOT)),a.get("feed"),Boolean.parseBoolean(a.getOrDefault("rule","false"))).snapshot();}
            case "list" -> {return new Result("SUCCESS",JsonCodec.encode(c.listCases()));}
            case "open" -> {return new Result("SUCCESS",JsonCodec.encode(c.open(UUID.fromString(required(a,"case"))).snapshot()));}
            case "inventory" -> {c.inventory(new HashSet<>(List.of(required(a,"criticalHosts").split(","))));result="Inventory accepted for this process; resupply on subsequent CLI runs";}
            default -> {
                UUID id=a.containsKey("case")?UUID.fromString(a.get("case")):c.current().id();
                switch(action.name()){
                    case "import" -> result=c.importLogs(id,Path.of(required(a,"file")));
                    case "query" -> {Object tree=JsonCodec.tree(required(a,"args"));var args=new HashMap<String,String>();if(!(tree instanceof Map<?,?> values))throw new IllegalArgumentException("Tool arguments must be one JSON object.");for(var entry:values.entrySet()){if(!(entry.getKey() instanceof String key)||!(entry.getValue() instanceof String value))throw new IllegalArgumentException("Tool arguments must be strings.");args.put(key,value);}result=c.execute(id,new ToolCall(UUID.randomUUID(),required(a,"tool"),args));}
                    case "resume" -> result=c.resume(id);
                    case "run" -> {if(a.containsKey("criticalHosts"))c.inventory(new HashSet<>(List.of(a.get("criticalHosts").split(","))));result=c.run(id);}
                    case "ask" -> result=c.ask(id,required(a,"question"));
                    case "rule" -> result=c.acceptRulePlan(id);
                    case "plan" -> result=c.replan(id);
                    case "clarify" -> result=c.clarifyScope(id,new CaseScope(required(a,"dataset"),required(a,"version"),required(a,"target"),Instant.parse(required(a,"start")),Instant.parse(required(a,"end"))));
                    case "revise" -> {var s=c.current();c.revise(id,new CaseScope(s.scope().datasetId(),s.scope().datasetVersion(),required(a,"target"),Instant.parse(required(a,"start")),Instant.parse(required(a,"end"))));result=c.current();}
                    case "save" -> result=c.save(id);
                    case "show" -> {return new Result("SUCCESS",JsonCodec.encode(c.current()));}
                    case "preview" -> {return new Result("SUCCESS",new ReportService().preview(c.current()));}
                    case "export" -> {Path p=Path.of(required(a,"out"));result=c.export(id,p,Boolean.parseBoolean(a.getOrDefault("overwrite","false"))).toString();}
                    default -> throw new IllegalArgumentException("Unknown action.");
                }
            }
        }
        c.save(c.current().id());return new Result(result instanceof Summary summary && !summary.successful()?"REJECTED":"SUCCESS",JsonCodec.encode(result));
    }
    private static String required(Map<String,String> a,String k){String v=a.get(k);if(v==null||v.isBlank())throw new IllegalArgumentException("Missing "+k);return v;}
}
