package sentinel.domain;

import java.time.Instant;

/** A recorded question with exactly one accepted answer or explicit failure. */
public record ConversationEntry(Instant timestamp, String question, Answer answer, String failure) {
    public ConversationEntry {
        DomainValidation.required(timestamp, "timestamp");
        DomainValidation.requiredText(question, "question");
        if ((answer == null) == (failure == null)) {
            throw new IllegalArgumentException("Conversation entry requires exactly one answer or failure.");
        }
        if (failure != null) DomainValidation.requiredText(failure, "failure");
    }
}
