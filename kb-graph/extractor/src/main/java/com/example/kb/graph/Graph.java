package com.example.kb.graph;

import java.util.List;

/** Nemenný graf – výstup extraktora aj čítačky lekcií; Neo4j sa plní len z neho. */
public record Graph(List<Node> nodes, List<Edge> edges) {

    public Graph {
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
    }

    /** Zlúči dva grafy (štruktúru a lekcie); rovnaké uzly spojí, duplicitné hrany vynechá. */
    public Graph merge(Graph other) {
        GraphBuilder builder = new GraphBuilder();
        builder.addAll(this);
        builder.addAll(other);
        return builder.build();
    }

    /** Vráti počet uzlov s daným labelom. */
    public long count(String label) {
        return nodes.stream().filter(n -> n.label().equals(label)).count();
    }

    /** Zistí, či graf obsahuje uzol. */
    public boolean contains(NodeRef ref) {
        return nodes.stream().anyMatch(n -> n.ref().equals(ref));
    }
}
