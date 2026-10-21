package com.example.kb.lessons;

import com.example.kb.graph.Graph;
import com.example.kb.graph.GraphBuilder;
import com.example.kb.graph.NodeRef;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Ručne písaná vrstva grafu (lekcie z poznamky/07, koncepty z poznamky/05). Na rozdiel od štruktúry
 * ju generovať nejde – je to úsudok človeka; ukotvenie na štruktúru ju ale robí vyhľadateľnou.
 */
public final class LessonReader {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private LessonReader() {
    }

    /** Prečíta topics.json, pitfalls.json a concepts.json (chýbajúci súbor = prázdny zoznam). */
    public static Graph read(Path lessonsDir) {
        GraphBuilder graph = new GraphBuilder();
        Set<String> seen = new HashSet<>();
        for (JsonNode t : array(lessonsDir.resolve("topics.json"))) {
            unique(seen, "Topic", t);
            graph.node("Topic", t.path("id").asText(), Map.of("title", t.path("title").asText()));
        }
        for (JsonNode p : array(lessonsDir.resolve("pitfalls.json"))) {
            unique(seen, "Lesson", p);
            addPitfall(p, graph);
        }
        for (JsonNode c : array(lessonsDir.resolve("concepts.json"))) {
            unique(seen, "Concept", c);
            addConcept(c, graph);
        }
        return graph.build();
    }

    private static void addPitfall(JsonNode p, GraphBuilder graph) {
        String id = p.path("id").asText();
        String note = p.path("note").asText("07-pasce.md");
        String number = id.replaceFirst("^\\D*0*", "");
        Map<String, Object> props = new HashMap<>();
        props.put("title", p.path("title").asText());
        props.put("text", p.path("text").asText());
        props.put("source", "poznamky/" + note + "#" + number);
        graph.node("Lesson", id, props);
        NodeRef self = new NodeRef("Lesson", id);
        p.path("topics").forEach(t -> graph.edge("ABOUT", self, new NodeRef("Topic", t.asText())));
        p.path("anchors").forEach(a -> graph.edge("ANCHORED_TO", self, ref(a)));
        graph.edge("DESCRIBED_IN", self, new NodeRef("Note", note));
    }

    private static void addConcept(JsonNode c, GraphBuilder graph) {
        String id = c.path("id").asText();
        Map<String, Object> props = new HashMap<>();
        props.put("name", c.path("name").asText());
        props.put("side", c.path("side").asText());
        if (c.hasNonNull("description")) {
            props.put("description", c.path("description").asText());
        }
        graph.node("Concept", id, props);
        NodeRef self = new NodeRef("Concept", id);
        c.path("replacedBy").forEach(r -> graph.edge("REPLACED_BY", self, new NodeRef("Concept", r.asText())));
        c.path("seenIn").forEach(s -> graph.edge("SEEN_IN", self, ref(s)));
        if (c.hasNonNull("note")) {
            graph.edge("DESCRIBED_IN", self, new NodeRef("Note", c.path("note").asText()));
        }
    }

    /** Skopírovaná lekcia s nezmeneným id by inak potichu prepísala pôvodnú (GraphBuilder vlastnosti zlučuje). */
    private static void unique(Set<String> seen, String label, JsonNode item) {
        String id = item.path("id").asText();
        if (!seen.add(label + ":" + id)) {
            throw new IllegalArgumentException("Duplicitné id v lekciách: " + label + " " + id);
        }
    }

    private static NodeRef ref(JsonNode anchor) {
        return new NodeRef(anchor.path("label").asText(), anchor.path("id").asText());
    }

    private static JsonNode array(Path file) {
        if (!Files.isRegularFile(file)) {
            return MAPPER.createArrayNode();
        }
        try {
            return MAPPER.readTree(file.toFile());
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Neplatný JSON v " + file + ": " + e.getOriginalMessage()
                    + " (riadok " + e.getLocation().getLineNr() + ", stĺpec " + e.getLocation().getColumnNr() + ")", e);
        } catch (IOException e) {
            throw new UncheckedIOException("Súbor lekcií sa nedá prečítať: " + file, e);
        }
    }
}
