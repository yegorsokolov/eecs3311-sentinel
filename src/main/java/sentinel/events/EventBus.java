package sentinel.events;

import java.nio.file.Path;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import sentinel.domain.*;

/** Stage 1 contract scaffold; business behavior is pending. */
public class EventBus {
    private final List<ProgressListener> listeners = new ArrayList<>();
    public void subscribe(ProgressListener listener) {
        throw new UnsupportedOperationException("Skeleton only: implementation pending");
    }
    public void unsubscribe(ProgressListener listener) {
        throw new UnsupportedOperationException("Skeleton only: implementation pending");
    }
    public void publish(ProgressEvent event) {
        throw new UnsupportedOperationException("Skeleton only: implementation pending");
    }
}
