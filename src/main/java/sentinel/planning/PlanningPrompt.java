package sentinel.planning;

import com.fasterxml.jackson.core.JsonFactory;
import java.io.*;
import java.nio.charset.StandardCharsets;
import sentinel.data.CaseEvidence;
import sentinel.domain.*;

/** Bundle-owned instructions with bounded, JSON-encoded untrusted case context. */
public final class PlanningPrompt {
    public static final String VERSION = "planning-v1";
    private PlanningPrompt() { }

    public static Prompt build(CaseContext context, PlanValidator validator, String repairCode) {
        var snapshot = context.snapshot();
        if (snapshot.goal().length() > 8192) throw invalid("Analyst goal is too large for bounded planning.");
        String template;
        try (var source = PlanningPrompt.class.getResourceAsStream("/prompts/planning.md")) {
            if (source == null) throw new IOException();
            byte[] bytes = source.readNBytes(16_385);
            if (bytes.length > 16_384) throw new IOException();
            template = new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException ex) { throw invalid("Versioned planning instructions are unavailable."); }
        StringWriter out = new StringWriter();
        try (var json = new JsonFactory().createGenerator(out)) {
            json.writeStartObject();
            json.writeStringField("goal", snapshot.goal());
            json.writeStringField("targetType", snapshot.targetType().name());
            json.writeObjectFieldStart("scope");
            json.writeStringField("datasetId", snapshot.scope().datasetId());
            json.writeStringField("datasetVersion", snapshot.scope().datasetVersion());
            json.writeStringField("target", snapshot.scope().target());
            json.writeStringField("start", snapshot.scope().start().toString());
            json.writeStringField("end", snapshot.scope().end().toString());
            json.writeEndObject();
            json.writeStringField("feedVersion", snapshot.feedVersion());
            json.writeNumberField("maxSteps", validator.stepLimit(context));
            json.writeArrayFieldStart("allowedTools");
            for (String tool : validator.allowedTools().stream().sorted().toList()) json.writeString(tool);
            json.writeEndArray();
            json.writeArrayFieldStart("acceptedIndicators");
            for (String indicator : CaseEvidence.indicators(validator.datasetStore(), snapshot).stream().sorted().limit(100).toList()) {
                json.writeString(indicator);
            }
            json.writeEndArray();
            json.writeEndObject();
        } catch (IOException ex) { throw invalid("Cannot encode bounded planning context."); }
        String correction = repairCode == null ? "" : "\nThe previous draft failed validation (" + repairCode
                + "). Return one complete corrected replacement. Do not repeat unsupported calls.\n";
        return new Prompt(VERSION, repairCode == null ? "planning" : "planning-repair",
                template + correction + "\nUNTRUSTED_CASE_CONTEXT_JSON\n" + out);
    }

    private static PlanningException invalid(String message) {
        return new PlanningException(PlanningException.Code.INVALID_CONTEXT, message);
    }
}
