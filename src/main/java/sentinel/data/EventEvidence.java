package sentinel.data;

import com.fasterxml.jackson.core.JsonFactory;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import sentinel.domain.Event;
import sentinel.domain.Evidence;

/** Lossless JSON event payload and stable identity within a dataset read version. */
final class EventEvidence {
    private static final JsonFactory JSON = new JsonFactory();
    private EventEvidence() { }

    static Evidence from(String datasetId, String readVersion, Event event) {
        String identity = datasetId.length() + ":" + datasetId + readVersion.length() + ":"
                + readVersion + event.eventId().length() + ":" + event.eventId();
        UUID id = UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8));
        StringWriter out = new StringWriter();
        try (var json = JSON.createGenerator(out)) {
            json.writeStartObject();
            json.writeStringField("eventId", event.eventId());
            json.writeStringField("timestamp", event.timestamp().toString());
            json.writeStringField("eventType", event.eventType());
            json.writeStringField("accountId", event.accountId());
            json.writeStringField("sourceIp", event.sourceIp());
            json.writeStringField("hostId", event.hostId());
            json.writeStringField("outcome", event.outcome());
            json.writeStringField("sourcePosition", event.sourcePosition());
            json.writeStringField("datasetVersion", event.datasetVersion());
            json.writeEndObject();
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot encode in-memory event evidence.", ex);
        }
        return new Evidence(id, event.eventId(), readVersion, event.sourcePosition(), out.toString());
    }
}
