package com.example.kb.graph;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GraphJsonTest {

    private static Graph sample() {
        return new GraphBuilder()
                .node("Service", "redis", Map.of("image", "redis:7.4-alpine", "ports", "6379:6379"))
                .node("Service", "crm-web", Map.of("build", "../crm-web"))
                .edge("DEPENDS_ON", new NodeRef("Service", "crm-web"), new NodeRef("Service", "redis"))
                .build();
    }

    @Test
    @DisplayName("Zápis a čítanie JSON vráti rovnaký graf a rovnaký text")
    void should_roundTrip_whenWrittenAndRead() {
        String json = GraphJson.write(sample());

        assertThat(GraphJson.read(json)).isEqualTo(sample());
        assertThat(GraphJson.write(GraphJson.read(json))).isEqualTo(json);
    }

    @Test
    @DisplayName("JSON končí novým riadkom a používa LF")
    void should_endWithLf_whenWritten() {
        String json = GraphJson.write(sample());

        assertThat(json).endsWith("\n").doesNotContain("\r");
    }

    @Test
    @DisplayName("Prázdny graf sa zapíše ako prázdne polia")
    void should_writeEmptyArrays_whenGraphEmpty() {
        Graph empty = new GraphBuilder().build();

        Graph read = GraphJson.read(GraphJson.write(empty));

        assertThat(read.nodes()).isEmpty();
        assertThat(read.edges()).isEmpty();
    }
}
