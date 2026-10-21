package com.example.kb.graph;

import java.util.Map;

/** Uzol grafu; vlastnosti sú zoradené podľa kľúča, aby bol JSON deterministický. */
public record Node(String label, String id, Map<String, Object> props) {

    /** Vráti odkaz na tento uzol. */
    public NodeRef ref() {
        return new NodeRef(label, id);
    }
}
