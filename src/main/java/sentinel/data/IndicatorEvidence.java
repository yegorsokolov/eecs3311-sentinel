package sentinel.data;

import com.fasterxml.jackson.core.JsonFactory;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import sentinel.domain.Evidence;
import sentinel.domain.IndicatorRecord;

/** Stable record identity and lossless source payload, independent of lookup time. */
final class IndicatorEvidence {
    private IndicatorEvidence() { }

    static Evidence from(IndicatorFeedLoader.Entry entry) {
        IndicatorRecord record = entry.record();
        String identity = "indicator:" + record.feedVersion().length() + ":" + record.feedVersion()
                + record.indicator().length() + ":" + record.indicator();
        StringWriter out = new StringWriter();
        try (var json = new JsonFactory().createGenerator(out)) {
            json.writeStartObject();
            json.writeStringField("indicator", record.indicator());
            json.writeStringField("verdict", record.verdict());
            json.writeStringField("source", record.source());
            json.writeStringField("observedAt", record.observedAt().toString());
            json.writeStringField("feedVersion", record.feedVersion());
            json.writeEndObject();
        } catch (IOException ex) { throw new IllegalStateException("Cannot encode indicator evidence.", ex); }
        return new Evidence(UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8)),
                record.indicator(), record.feedVersion(), entry.provenance(), out.toString());
    }
}
