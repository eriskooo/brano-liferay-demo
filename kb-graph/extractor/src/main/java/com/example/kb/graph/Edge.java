package com.example.kb.graph;

import java.util.Comparator;

/** Orientovaná hrana (vzťah v Neo4j) medzi dvoma uzlami. */
public record Edge(String type, NodeRef from, NodeRef to) implements Comparable<Edge> {

    private static final Comparator<Edge> ORDER = Comparator.comparing(Edge::type)
            .thenComparing(Edge::from)
            .thenComparing(Edge::to);

    @Override
    public int compareTo(Edge other) {
        return ORDER.compare(this, other);
    }
}
