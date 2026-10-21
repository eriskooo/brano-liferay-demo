package com.example.kb.lessons;

import com.example.kb.graph.Edge;
import com.example.kb.graph.Graph;
import com.example.kb.graph.Node;
import com.example.kb.graph.NodeRef;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Kotva lekcie na uzol, ktorý už v kóde nie je = zastaraná lekcia (efekt z článku). Overuje sa v pamäti nad
 * zlúčeným grafom ešte pred zmazaním Neo4j, takže pri chybe ostane databáza nedotknutá.
 */
public final class AnchorValidator {

    private AnchorValidator() {
    }

    /** Vráti popis každej hrany, ktorej koncový uzol v grafe chýba; prázdny zoznam = všetko sedí. */
    public static List<String> missing(Graph combined) {
        Set<NodeRef> known = combined.nodes().stream().map(Node::ref).collect(Collectors.toSet());
        return combined.edges().stream()
                .filter(e -> !known.contains(e.from()) || !known.contains(e.to()))
                .map(AnchorValidator::describe)
                .toList();
    }

    private static String describe(Edge e) {
        return e.from().id() + " -[" + e.type() + "]-> " + e.to();
    }
}
