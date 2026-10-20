package sentinel;

import java.io.PrintStream;
import sentinel.config.AppConfig;
import sentinel.config.ConfigurationException;

/** Startup diagnostics; investigation interfaces will be connected incrementally. */
public final class Main {
    private Main() { }

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        if (args.length == 0 || (args.length == 1 && args[0].equals("--help"))) {
            out.println("Usage: java -jar sentinel-0.1.0-SNAPSHOT.jar --check-config");
            out.println("GUI, CLI investigation commands and Ollama integration are not implemented yet.");
            return 0;
        }
        if (args.length != 1 || !args[0].equals("--check-config")) {
            err.println("Unsupported command. Use --help or --check-config.");
            return 2;
        }
        try {
            AppConfig config = AppConfig.load();
            out.println("Configuration valid (Ollama connectivity not checked).");
            out.println("Ollama URL: " + config.ollamaUrl());
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
