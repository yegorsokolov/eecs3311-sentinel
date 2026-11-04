package sentinel;

import java.io.PrintStream;
import sentinel.config.AppConfig;
import sentinel.config.ConfigurationException;

/** Shared-controller CLI/Swing workflows and startup diagnostics. */
public final class Main {
    private Main() { }

    public static void main(String[] args) {
        int code=run(args,System.out,System.err);
        if(args.length==0 || !args[0].equals("gui") || code!=0)System.exit(code);
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        if(args.length>0 && java.util.Set.of("gui","new","import","query","run","ask","resume","rule","plan","clarify","revise","save","open","show","list","preview","export","inventory").contains(args[0])) {
            sentinel.application.ApplicationSession session=null;
            try {
                var action=sentinel.presentation.SentinelCLI.parse(args);var config=AppConfig.load();String dataset=action.arguments().getOrDefault("dataset","local");
                if(action.arguments().containsKey("case")){var s=new sentinel.persistence.JsonCaseRepository(config.caseDirectory()).load(java.util.UUID.fromString(action.arguments().get("case"))).snapshot();if(s.scope()!=null)dataset=s.scope().datasetId();}
                session=new sentinel.application.ApplicationSession(config,dataset);
                if(args[0].equals("gui")){var owned=session;String selected=dataset;javax.swing.SwingUtilities.invokeLater(()->{var gui=new sentinel.presentation.SentinelGUI(owned.controller);owned.events.subscribe(gui);gui.show(selected,owned::close);});return 0;}
                var cli=new sentinel.presentation.SentinelCLI(session.controller,err);session.events.subscribe(cli);
                if(action.arguments().containsKey("case")&&!args[0].equals("open"))session.controller.open(java.util.UUID.fromString(action.arguments().get("case")));
                var result=cli.submit(action);out.println(sentinel.persistence.JsonCodec.encode(result));return result.status().equals("SUCCESS")?0:2;
            } catch(IllegalArgumentException ex){err.println("Invalid input: "+ex.getMessage());return 2;}
            catch(sentinel.tools.ToolException ex){err.println("Tool failure: "+ex.code());return java.util.Set.of(sentinel.tools.ToolException.Code.INVALID_ARGUMENT,sentinel.tools.ToolException.Code.UNKNOWN_TOOL,sentinel.tools.ToolException.Code.SCOPE_REQUIRED,sentinel.tools.ToolException.Code.OUT_OF_SCOPE).contains(ex.code())?2:3;}
            catch(RuntimeException ex){err.println("Operational failure: "+(ex instanceof sentinel.llm.ModelException m?m.code().name():"Check local data, case state, permissions or model availability."));return 3;}
            finally{if(session!=null&&!args[0].equals("gui"))session.close();}
        }
        if (args.length == 0 || (args.length == 1 && args[0].equals("--help"))) {
            out.println("Usage: java -jar sentinel-0.1.0-SNAPSHOT.jar --check-config");
            out.println("Commands: gui, new, import, query, rule, plan, clarify, revise, run, ask, resume, save, open, show, list, preview, export. Options use --name value. new supports --rule; export --overwrite confirms replacement.");
            return 0;
        }
        if (args.length != 1 || !args[0].equals("--check-config")) {
            err.println("Unsupported command. Use --help or --check-config.");
            return 2;
        }
        try {
            AppConfig config = AppConfig.load();
            out.println("Configuration valid (model connectivity not checked).");
            out.println("Model URL: " + config.ollamaUrl());
            out.println("Model: " + config.ollamaModel());
            out.println("Datasets: " + config.datasetDirectory());
            out.println("Cases: " + config.caseDirectory());
            out.println("Exports: " + config.exportDirectory());
            out.println("Execution limits: " + config.limits());
            return 0;
        } catch (ConfigurationException ex) {
            err.println("Configuration error: " + ex.getMessage());
            return 2;
        }
    }
}
