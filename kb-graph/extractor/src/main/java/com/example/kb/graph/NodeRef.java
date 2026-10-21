package com.example.kb.graph;

/** Odkaz na uzol: label + id. V Neo4j je id unikátne len v rámci labelu, preto ho vždy nosíme spolu. */
public record NodeRef(String label, String id) implements Comparable<NodeRef> {

    @Override
    public int compareTo(NodeRef other) {
        int byLabel = label.compareTo(other.label);
        return byLabel != 0 ? byLabel : id.compareTo(other.id);
    }

    @Override
    public String toString() {
        return label + ":" + id;
    }
}
