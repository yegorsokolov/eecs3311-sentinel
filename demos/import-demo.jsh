import sentinel.data.*;
import java.nio.file.Path;
var store = new DatasetStore("demo");
var importer = new LogImporter(store);
System.out.println("1. Valid: " + importer.importFile(Path.of("data/samples/import-valid.json")));
System.out.println("2. First timestamp: " + store.events("v1").get(0).timestamp());
System.out.println("3. First source: " + store.events("v1").get(0).sourcePosition());
System.out.println("4. Reimport: " + importer.importFile(Path.of("data/samples/import-valid.json")));
System.out.println("5. Conflict: " + importer.importFile(Path.of("data/samples/import-conflict.json")));
System.out.println("6. Invalid timestamp: " + importer.importFile(Path.of("data/samples/import-invalid.json")));
System.out.println("7. Final version/count: " + store.currentVersion() + "/" + store.events(store.currentVersion()).size());
/exit
