package com.example.kb.graph;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GraphBuilderTest {

    @Test
    @DisplayName("Zlúči vlastnosti, keď sa rovnaký uzol pridá dvakrát")
    void should_mergeProps_whenSameNodeAddedTwice() {
        Graph g = new GraphBuilder()
                .node("Service", "redis", Map.of("image", "redis:7.4-alpine"))
                .node("Service", "redis", Map.of("ports", "6379:6379"))
                .build();

        assertThat(g.nodes()).hasSize(1);
        assertThat(g.nodes().get(0).props())
                .containsEntry("image", "redis:7.4-alpine")
                .containsEntry("ports", "6379:6379");
    }

    @Test
    @DisplayName("Ignoruje duplicitnú hranu")
    void should_ignoreDuplicateEdge_whenAddedTwice() {
        NodeRef a = new NodeRef("Service", "a");
        NodeRef b = new NodeRef("Service", "b");
        Graph g = new GraphBuilder()
                .node("Service", "a", Map.of())
                .node("Service", "b", Map.of())
                .edge("DEPENDS_ON", a, b)
                .edge("DEPENDS_ON", a, b)
                .build();

        assertThat(g.edges()).hasSize(1);
    }

    @Test
    @DisplayName("Zoradí uzly podľa labelu a id bez ohľadu na poradie pridania")
    void should_sortNodes_whenBuilt() {
        Graph g = new GraphBuilder()
                .node("Service", "b", Map.of())
                .node("Module", "z", Map.of())
                .node("Service", "a", Map.of())
                .build();

        assertThat(g.nodes()).extracting(Node::ref).containsExactly(
                new NodeRef("Module", "z"), new NodeRef("Service", "a"), new NodeRef("Service", "b"));
    }

    @Test
    @DisplayName("Zoradí hrany podľa typu, zdroja a cieľa")
    void should_sortEdges_whenBuilt() {
        NodeRef a = new NodeRef("Service", "a");
        NodeRef b = new NodeRef("Service", "b");
        Graph g = new GraphBuilder()
                .edge("ROUTES", a, b)
                .edge("DEPENDS_ON", b, a)
                .edge("DEPENDS_ON", a, b)
                .build();

        assertThat(g.edges()).containsExactly(
                new Edge("DEPENDS_ON", a, b), new Edge("DEPENDS_ON", b, a), new Edge("ROUTES", a, b));
    }

    @Test
    @DisplayName("Zlúčenie dvoch grafov obsahuje uzly a hrany oboch")
    void should_containBoth_whenMerged() {
        Graph left = new GraphBuilder().node("Service", "a", Map.of()).build();
        Graph right = new GraphBuilder().node("Topic", "session", Map.of())
                .edge("ABOUT", new NodeRef("Lesson", "x"), new NodeRef("Topic", "session")).build();

        Graph merged = left.merge(right);

        assertThat(merged.count("Service")).isEqualTo(1);
        assertThat(merged.count("Topic")).isEqualTo(1);
        assertThat(merged.edges()).hasSize(1);
        assertThat(merged.contains(new NodeRef("Topic", "session"))).isTrue();
        assertThat(merged.contains(new NodeRef("Topic", "redis"))).isFalse();
    }
}
