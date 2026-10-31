package sentinel.domain;

/** Versioned application-owned instructions; retrieved context remains untrusted data. */
public record Prompt(String version, String purpose, String content) {
    public Prompt {
        DomainValidation.requiredText(version, "version");
        DomainValidation.requiredText(purpose, "purpose");
        DomainValidation.requiredText(content, "content");
        if (!version.matches("[A-Za-z0-9._-]{1,64}") || !purpose.matches("[A-Za-z0-9._-]{1,64}")) {
            throw new IllegalArgumentException("Prompt version/purpose must be bounded identifiers.");
        }
    }
}
