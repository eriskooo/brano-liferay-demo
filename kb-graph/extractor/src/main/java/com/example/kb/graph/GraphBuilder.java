package com.example.kb.graph;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Skladá graf z viacerých extraktorov; zabezpečí, že uzol aj hrana sú v grafe len raz a výstup je zoradený. */
public class GraphBuilder {

    private final Map<NodeRef, TreeMap<String, Object>> nodes = new LinkedHashMap<>();
    private final Set<Edge> edges = new LinkedHashSet<>();

    /** Pridá uzol alebo doplní vlastnosti existujúceho uzla s rovnakým labelom a id. */
    public GraphBuilder node(String label, String id, Map<String, Object> props) {
        nodes.computeIfAbsent(new NodeRef(label, id), k -> new TreeMap<>()).putAll(props);
        return this;
    }

    /** Pridá hranu; duplicitu ignoruje. */
    public GraphBuilder edge(String type, NodeRef from, NodeRef to) {
        edges.add(new Edge(type, from, to));
        return this;
    }

    /** Zistí, či už uzol existuje – extraktory podľa toho vytvárajú hrany len na známe uzly. */
    public boolean has(NodeRef ref) {
        return nodes.containsKey(ref);
    }

    void addAll(Graph graph) {
        graph.nodes().forEach(n -> node(n.label(), n.id(), n.props()));
        graph.edges().forEach(e -> edge(e.type(), e.from(), e.to()));
    }

    /** Vráti zoradený nemenný graf. */
    public Graph build() {
        var sortedNodes = nodes.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new Node(e.getKey().label(), e.getKey().id(),
                        Collections.unmodifiableMap(new TreeMap<>(e.getValue()))))
                .toList();
        var sortedEdges = edges.stream().sorted().toList();
        return new Graph(sortedNodes, sortedEdges);
    }
}
