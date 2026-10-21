package com.example.kb.extract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.kb.graph.Edge;
import com.example.kb.graph.Graph;
import com.example.kb.graph.GraphJson;
import com.example.kb.graph.NodeRef;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Beží nad skutočným repom – overuje, že extraktor naozaj vidí to, čo v repe je. */
class StructureExtractorTest {

    private static Graph graph;

    @BeforeAll
    static void extract() {
        graph = StructureExtractor.extract(Fixtures.repoRoot());
    }

    private static NodeRef ref(String label, String id) {
        return new NodeRef(label, id);
    }

    @Test
    @DisplayName("Skutočné repo prekročí všetky prahy")
    void should_passThresholds_whenRunOnRepo() {
        StructureExtractor.MIN_COUNTS.forEach((label, min) ->
                assertThat(graph.count(label)).as(label).isGreaterThanOrEqualTo(min));
    }

    @Test
    @DisplayName("Skutočné repo obsahuje kľúčové uzly a hrany")
    void should_containKeyFacts_whenRunOnRepo() {
        assertThat(graph.contains(ref("Service", "redis"))).isTrue();
        assertThat(graph.contains(ref("Portlet", "blacklist"))).isTrue();
        assertThat(graph.contains(ref("Endpoint", "GET /crm/blacklist/search"))).isTrue();
        assertThat(graph.contains(ref("Note", "07-pasce.md"))).isTrue();
        assertThat(graph.edges()).contains(
                new Edge("PROXIES_TO", ref("Route", "/crm/"), ref("Service", "crm-web")),
                new Edge("DEPENDS_ON", ref("Service", "crm-web"), ref("Service", "postgres")),
                new Edge("OWNS", ref("Service", "postgres"), ref("Table", "blacklist.blacklist_record")),
                new Edge("CHILD_OF", ref("Module", "customercare-portlets"), ref("Module", "legacy-parent")),
                new Edge("BUILT_FROM", ref("Service", "crm-web"), ref("Module", "crm-web")));
    }

    @Test
    @DisplayName("Testové fixtures extraktora nie sú súčasťou štruktúry repa")
    void should_skipTestFixtures_whenRunOnRepo() {
        assertThat(graph.contains(ref("Module", "fx-parent"))).isFalse();
        assertThat(graph.nodes()).filteredOn(n -> n.label().equals("Module"))
                .allSatisfy(n -> assertThat((String) n.props().get("path")).doesNotContain("src/test"));
    }

    @Test
    @DisplayName("Žiadny uzol nepochádza z klientskych podkladov v input/")
    void should_notReadInput_whenRunOnRepo() {
        assertThat(graph.nodes()).allSatisfy(n -> {
            assertThat(n.id()).doesNotStartWith("input/");
            assertThat(String.valueOf(n.props().getOrDefault("path", ""))).doesNotStartWith("input");
        });
    }

    @Test
    @DisplayName("Cesty používajú lomky bez ohľadu na OS")
    void should_useForwardSlashes_whenRunOnAnyOs() {
        assertThat(graph.nodes()).filteredOn(n -> n.label().equals("File"))
                .allSatisfy(n -> assertThat(n.id()).doesNotContain("\\").doesNotStartWith("/"));
        assertThat(graph.nodes()).filteredOn(n -> n.label().equals("Module"))
                .allSatisfy(n -> assertThat((String) n.props().get("path")).doesNotContain("\\").doesNotStartWith("/"));
    }

    @Test
    @DisplayName("Dva behy dajú identický JSON")
    void should_beDeterministic_whenRunTwice() {
        assertThat(GraphJson.write(StructureExtractor.extract(Fixtures.repoRoot()))).isEqualTo(GraphJson.write(graph));
    }

    @Test
    @DisplayName("Malý graf neprejde prahom a nezapíše sa")
    void should_fail_whenBelowThreshold() {
        assertThatThrownBy(() -> StructureExtractor.extract(Fixtures.dir("poms")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("minimum");
    }
}
