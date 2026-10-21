package com.example.kb.lessons;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.kb.graph.Graph;
import com.example.kb.graph.GraphBuilder;
import com.example.kb.graph.NodeRef;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AnchorValidatorTest {

    private static final NodeRef LESSON = new NodeRef("Lesson", "pitfall-01");

    @Test
    @DisplayName("Neexistujúca kotva sa nahlási s presným popisom")
    void should_reportMissing_whenAnchorUnknown() {
        Graph g = new GraphBuilder()
                .node("Lesson", "pitfall-01", Map.of())
                .edge("ANCHORED_TO", LESSON, new NodeRef("Service", "neexistuje"))
                .build();

        assertThat(AnchorValidator.missing(g)).containsExactly("pitfall-01 -[ANCHORED_TO]-> Service:neexistuje");
    }

    @Test
    @DisplayName("Všetky chýbajúce kotvy sa nahlásia naraz")
    void should_reportAll_whenSeveralMissing() {
        Graph g = new GraphBuilder()
                .node("Lesson", "pitfall-01", Map.of())
                .edge("ANCHORED_TO", LESSON, new NodeRef("Service", "a"))
                .edge("ABOUT", LESSON, new NodeRef("Topic", "b"))
                .build();

        assertThat(AnchorValidator.missing(g)).hasSize(2);
    }

    @Test
    @DisplayName("Platné kotvy vrátia prázdny zoznam")
    void should_returnEmpty_whenAllAnchorsExist() {
        Graph g = new GraphBuilder()
                .node("Lesson", "pitfall-01", Map.of())
                .node("Service", "redis", Map.of())
                .edge("ANCHORED_TO", LESSON, new NodeRef("Service", "redis"))
                .build();

        assertThat(AnchorValidator.missing(g)).isEmpty();
    }
}
