package sentinel.analysis;

import java.nio.file.Path;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import sentinel.domain.*;

/** Stage 1 contract scaffold; business behavior is pending. */
public class TimelineBuilder {
    private final int correlationWindowMinutes = 15;
    public List<Event> build(List<Evidence> evidence) {
        throw new UnsupportedOperationException("Skeleton only: implementation pending");
    }
}
