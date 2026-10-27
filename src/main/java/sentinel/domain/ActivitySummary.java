package sentinel.domain;

import java.time.Instant;

/** Counts cover all matching stored events, including those beyond the evidence cap.
 * The interval describes the query, not a guarantee of complete source logging.
 * Only exact outcome values "success" and "failure" are classified; others are unknown.
 */
public record ActivitySummary(String accountId, Instant start, Instant end, int totalEvents,
                              int returnedEvents, int successes, int failures, int unknownOutcomes,
                              int distinctHosts, int unknownHostEvents,
                              Instant firstEvent, Instant lastEvent) {
    public ActivitySummary {
        DomainValidation.requiredText(accountId, "accountId");
        DomainValidation.required(start, "start");
        DomainValidation.required(end, "end");
        if (end.isBefore(start) || totalEvents < 0 || returnedEvents < 0 || returnedEvents > totalEvents
                || successes < 0 || failures < 0 || unknownOutcomes < 0
                || (long) successes + failures + unknownOutcomes != totalEvents
                || distinctHosts < 0 || unknownHostEvents < 0 || unknownHostEvents > totalEvents
                || distinctHosts > totalEvents - unknownHostEvents) {
            throw new IllegalArgumentException("Invalid activity counts or interval.");
        }
        if (totalEvents == 0) {
            if (firstEvent != null || lastEvent != null) throw new IllegalArgumentException("Empty activity has no event times.");
        } else if (firstEvent == null || lastEvent == null || firstEvent.isBefore(start)
                || lastEvent.isAfter(end) || lastEvent.isBefore(firstEvent)) {
            throw new IllegalArgumentException("Activity times must lie within the requested interval.");
        }
    }
}
