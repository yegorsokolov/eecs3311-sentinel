package sentinel.persistence;

import java.nio.file.Path;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import sentinel.domain.*;

/** Stage 1 contract scaffold; business behavior is pending. */
public class JsonCaseRepository implements CaseRepository {
    private final Path root;
    public JsonCaseRepository(Path root) {
        this.root = Objects.requireNonNull(root);
    }
    public int save(CaseSnapshot snapshot) {
        throw new UnsupportedOperationException("Skeleton only: implementation pending");
    }
    public Investigation load(UUID id) {
        throw new UnsupportedOperationException("Skeleton only: implementation pending");
    }
    public List<CaseSummary> list() {
        throw new UnsupportedOperationException("Skeleton only: implementation pending");
    }
}
