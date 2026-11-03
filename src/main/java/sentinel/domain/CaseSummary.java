package sentinel.domain;
import java.util.UUID;
/** Validated compact case-list entry. */
public record CaseSummary(UUID id,int version,CaseStatus status,String goal) {
    public CaseSummary { DomainValidation.required(id,"id");DomainValidation.required(status,"status");DomainValidation.requiredText(goal,"goal");if(version<0)throw new IllegalArgumentException("Negative case version."); }
}
