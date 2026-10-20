package sentinel.presentation;

import java.nio.file.Path;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import sentinel.domain.*;
import sentinel.application.AgentController;
import sentinel.events.ProgressListener;

/** Stage 1 contract scaffold; business behavior is pending. */
public class SentinelGUI implements ProgressListener {
    private final AgentController controller;
    public SentinelGUI(AgentController controller) {
        this.controller = Objects.requireNonNull(controller);
    }
    public Result submit(Action action) {
        throw new UnsupportedOperationException("Skeleton only: implementation pending");
    }
    public void update(ProgressEvent event) {
        throw new UnsupportedOperationException("Skeleton only: implementation pending");
    }
}
