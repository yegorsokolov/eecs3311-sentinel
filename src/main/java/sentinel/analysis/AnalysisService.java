package sentinel.analysis;

import java.nio.file.Path;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import sentinel.domain.*;
import sentinel.llm.LLMGateway;

/** Stage 1 contract scaffold; business behavior is pending. */
public class AnalysisService {
    private final LLMGateway llm;
    private final TimelineBuilder timeline;
    private final SeverityPolicy severity;
    public AnalysisService(LLMGateway llm, TimelineBuilder timeline, SeverityPolicy severity) {
        this.llm = Objects.requireNonNull(llm);
        this.timeline = Objects.requireNonNull(timeline);
        this.severity = Objects.requireNonNull(severity);
    }
    public Assessment analyze(Investigation investigation) {
        throw new UnsupportedOperationException("Skeleton only: implementation pending");
    }
    public Answer answer(Investigation investigation, String question) {
        throw new UnsupportedOperationException("Skeleton only: implementation pending");
    }
    public boolean validateCitations(String response, Investigation investigation) {
        throw new UnsupportedOperationException("Skeleton only: implementation pending");
    }
}
