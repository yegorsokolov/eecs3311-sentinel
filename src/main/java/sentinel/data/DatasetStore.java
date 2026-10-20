package sentinel.data;

import java.nio.file.Path;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import sentinel.domain.*;

/** Stage 1 contract scaffold; business behavior is pending. */
public class DatasetStore {
    private String datasetVersion;
    private final List<Event> events = new ArrayList<>();
    private final List<IndicatorRecord> indicators = new ArrayList<>();
    public Summary ingest(ImportBatch batch) {
        throw new UnsupportedOperationException("Skeleton only: implementation pending");
    }
    public ToolResult search(ToolCall call) {
        throw new UnsupportedOperationException("Skeleton only: implementation pending");
    }
    public ToolResult lookup(ToolCall call) {
        throw new UnsupportedOperationException("Skeleton only: implementation pending");
    }
    public ToolResult activity(ToolCall call) {
        throw new UnsupportedOperationException("Skeleton only: implementation pending");
    }
}
