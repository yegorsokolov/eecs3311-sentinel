package sentinel.domain;

import java.nio.file.Path;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import sentinel.domain.*;

/** Stage 1 contract scaffold; business behavior is pending. */
public class Investigation {
    private UUID id;
    private CaseStatus status;
    private CaseScope scope;
    private Plan plan;
    private Assessment assessment;
    private final List<Evidence> evidence = new ArrayList<>();
    private final List<AuditEntry> history = new ArrayList<>();
    public void setPlan(Plan plan) {
        throw new UnsupportedOperationException("Skeleton only: implementation pending");
    }
    public void addEvidence(ToolResult result) {
        throw new UnsupportedOperationException("Skeleton only: implementation pending");
    }
    public void setAssessment(Assessment assessment) {
        throw new UnsupportedOperationException("Skeleton only: implementation pending");
    }
    public void record(AuditEntry entry) {
        throw new UnsupportedOperationException("Skeleton only: implementation pending");
    }
    public CaseSnapshot snapshot() {
        throw new UnsupportedOperationException("Skeleton only: implementation pending");
    }
}
