package sentinel.presentation;
import java.util.*;
import java.io.PrintStream;
import sentinel.domain.*;
import sentinel.application.*;
import sentinel.events.ProgressListener;
public final class SentinelCLI implements ProgressListener {
    private final ActionDispatcher dispatcher;private final PrintStream progress;
    public SentinelCLI(AgentController controller){this(controller,System.err);}
    public SentinelCLI(AgentController controller,PrintStream progress){dispatcher=new ActionDispatcher(controller);this.progress=progress;}
    public Result submit(Action action){return dispatcher.submit(action);}
    public void update(ProgressEvent event){progress.println(event.stage()+": "+event.outcome());}
    public static Action parse(String[] args){if(args.length==0)throw new IllegalArgumentException("Missing command");var values=new HashMap<String,String>();
        for(int i=1;i<args.length;i++){String name=args[i];if(!name.startsWith("--"))throw new IllegalArgumentException("Expected named argument");String key=name.substring(2);String value;
            if(Set.of("rule","overwrite").contains(key))value="true";else{if(++i>=args.length)throw new IllegalArgumentException("Missing option value");value=args[i];}
            if(values.putIfAbsent(key,value)!=null)throw new IllegalArgumentException("Duplicate option");}
        return new Action(args[0],values);
    }
}
