package com.example.kb.extract;

import com.example.kb.graph.Graph;
import com.example.kb.graph.GraphBuilder;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Spustí všetky extraktory nad repom. Zlyhá nahlas (podľa článku): radšej chyba než „úspešne“ zapísaný
 * poloprázdny graf, keď sa napr. presunie súbor a extraktor potichu nič nenájde.
 */
public final class StructureExtractor {

    /** Minimálne počty uzlov; pod nimi je extraktor pokazený, nie repo malé. */
    public static final Map<String, Integer> MIN_COUNTS = Map.of(
            "Module", 5, "Service", 6, "Route", 5, "Portlet", 3, "Endpoint", 3, "Table", 1, "Note", 10);

    // Poradie je dôležité: BUILT_FROM a DECLARES sa viažu len na moduly, ktoré už PomExtractor našiel.
    private static final List<Extractor> EXTRACTORS = List.of(
            new PomExtractor(), new ComposeExtractor(), new NginxExtractor(), new PortletExtractor(),
            new EndpointExtractor(), new SqlExtractor(), new NoteExtractor());

    private StructureExtractor() {
    }

    /** Vygeneruje štruktúrnu vrstvu grafu z repa; pri nedostatku uzlov hodí IllegalStateException. */
    public static Graph extract(Path repoRoot) {
        GraphBuilder builder = new GraphBuilder();
        try {
            for (Extractor extractor : EXTRACTORS) {
                extractor.extract(repoRoot, builder);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Graph graph = builder.build();
        MIN_COUNTS.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> {
            long found = graph.count(e.getKey());
            if (found < e.getValue()) {
                throw new IllegalStateException("Extraktor našiel len " + found + " uzlov " + e.getKey()
                        + " (minimum " + e.getValue() + ") – graf sa nezapíše");
            }
        });
        return graph;
    }
}
