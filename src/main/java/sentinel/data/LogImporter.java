package sentinel.data;

import java.nio.file.Path;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import sentinel.domain.*;

/** Stage 1 contract scaffold; business behavior is pending. */
public class LogImporter {
    private final DatasetStore store;
    public LogImporter(DatasetStore store) {
        this.store = Objects.requireNonNull(store);
    }
    public Summary importFile(Path path) {
        throw new UnsupportedOperationException("Skeleton only: implementation pending");
    }
}
