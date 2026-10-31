import sentinel.config.*;
import sentinel.domain.*;
import sentinel.llm.*;
var config = AppConfig.load();
var adapter = new OllamaAdapter(config.ollamaUrl(), config.ollamaModel());
try {
    var text = adapter.complete(new Prompt("diagnostic-v1", "diagnostic", "Return exactly one JSON object with a status field whose value is ok."));
    System.out.println("Local completion received: model=" + adapter.modelVersion() + " / characters=" + text.length());
} catch (ModelException ex) { System.out.println("Local completion unavailable: " + ex.code()); }
/exit
