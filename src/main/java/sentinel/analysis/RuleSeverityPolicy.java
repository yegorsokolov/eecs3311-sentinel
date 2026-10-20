package sentinel.analysis;

import java.nio.file.Path;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import sentinel.domain.*;

/** Stage 1 contract scaffold; business behavior is pending. */
public class RuleSeverityPolicy implements SeverityPolicy {
    private final String policyVersion = "1";
    public Severity assess(Facts facts) {
        throw new UnsupportedOperationException("Skeleton only: implementation pending");
    }
}
