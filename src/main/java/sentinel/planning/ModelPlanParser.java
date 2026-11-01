package sentinel.planning;

import com.fasterxml.jackson.core.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import sentinel.domain.*;

/** Strict bounded model draft parser; case revision, UUIDs and MODEL origin are application-owned. */
public final class ModelPlanParser {
    public static final int MAX_REPLY_BYTES = 64 * 1024;
    private static final JsonFactory JSON = JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(8)
                    .maxStringLength(MAX_REPLY_BYTES).maxNameLength(128).build()).build();
    private record Step(String label, String tool, Map<String, String> args, List<String> dependencies) { }
    private ModelPlanParser() { }

    public static Plan parse(String reply, CaseContext context) {
        if (reply == null || reply.length() > MAX_REPLY_BYTES
                || reply.getBytes(StandardCharsets.UTF_8).length > MAX_REPLY_BYTES) throw invalid();
        try (var parser = JSON.createParser(reply)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) throw invalid();
            String version = null;
            List<Step> rows = null;
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                if (parser.currentToken() != JsonToken.FIELD_NAME) throw invalid();
                String field = parser.currentName();
                JsonToken token = parser.nextToken();
                if ("version".equals(field)) {
                    if (token != JsonToken.VALUE_STRING) throw invalid();
                    version = parser.getText();
                } else if ("steps".equals(field)) {
                    if (token != JsonToken.START_ARRAY) throw invalid();
                    rows = new ArrayList<>();
                    while (parser.nextToken() != JsonToken.END_ARRAY) {
                        if (rows.size() >= 8) throw invalid();
                        rows.add(step(parser));
                    }
                } else throw invalid();
            }
            if (parser.nextToken() != null || !PlanningPrompt.VERSION.equals(version) || rows == null || rows.isEmpty()) throw invalid();
            var ids = new LinkedHashMap<String, UUID>();
            UUID planId = UUID.randomUUID();
            for (Step row : rows) {
                if (!row.label().matches("[A-Za-z][A-Za-z0-9_-]{0,63}") || ids.containsKey(row.label())) throw invalid();
                ids.put(row.label(), UUID.nameUUIDFromBytes((planId + ":" + row.label()).getBytes(StandardCharsets.UTF_8)));
            }
            var calls = new ArrayList<ToolCall>();
            var dependencies = new HashMap<UUID, List<UUID>>();
            for (Step row : rows) {
                calls.add(new ToolCall(ids.get(row.label()), row.tool(), row.args()));
                var prerequisites = new ArrayList<UUID>();
                for (String label : row.dependencies()) {
                    UUID id = ids.get(label);
                    if (id == null) throw invalid();
                    prerequisites.add(id);
                }
                if (!prerequisites.isEmpty()) dependencies.put(ids.get(row.label()), List.copyOf(prerequisites));
            }
            int revision = context.snapshot().plan() == null ? 0 : context.snapshot().plan().revision() + 1;
            return new Plan(planId, revision, calls, dependencies, Plan.Origin.MODEL);
        } catch (IOException | IllegalArgumentException ex) { throw invalid(); }
    }

    private static Step step(JsonParser parser) throws IOException {
        if (parser.currentToken() != JsonToken.START_OBJECT) throw invalid();
        String label = null, tool = null;
        Map<String, String> args = null;
        List<String> dependencies = null;
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            if (parser.currentToken() != JsonToken.FIELD_NAME) throw invalid();
            String field = parser.currentName();
            JsonToken token = parser.nextToken();
            switch (field) {
                case "id", "tool" -> {
                    if (token != JsonToken.VALUE_STRING || parser.getText().isBlank() || parser.getTextLength() > 64) throw invalid();
                    if ("id".equals(field)) label = parser.getText(); else tool = parser.getText();
                }
                case "args" -> {
                    if (token != JsonToken.START_OBJECT) throw invalid();
                    args = new HashMap<>();
                    while (parser.nextToken() != JsonToken.END_OBJECT) {
                        if (parser.currentToken() != JsonToken.FIELD_NAME || args.size() >= 16) throw invalid();
                        String name = parser.currentName();
                        if (parser.nextToken() != JsonToken.VALUE_STRING) throw invalid();
                        args.put(name, parser.getText());
                    }
                }
                case "dependsOn" -> {
                    if (token != JsonToken.START_ARRAY) throw invalid();
                    dependencies = new ArrayList<>();
                    while (parser.nextToken() != JsonToken.END_ARRAY) {
                        if (parser.currentToken() != JsonToken.VALUE_STRING || dependencies.size() >= 8) throw invalid();
                        dependencies.add(parser.getText());
                    }
                }
                default -> throw invalid();
            }
        }
        if (label == null || tool == null || args == null || dependencies == null) throw invalid();
        return new Step(label, tool, Map.copyOf(args), List.copyOf(dependencies));
    }

    private static PlanningException invalid() {
        return new PlanningException(PlanningException.Code.INVALID_PLAN, "Model draft does not match the bounded planning schema.");
    }
}
