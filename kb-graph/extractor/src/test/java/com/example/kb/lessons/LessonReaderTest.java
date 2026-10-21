package com.example.kb.lessons;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.kb.graph.Edge;
import com.example.kb.graph.Graph;
import com.example.kb.graph.Node;
import com.example.kb.graph.NodeRef;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LessonReaderTest {

    private static Graph graph;

    @BeforeAll
    static void read() throws URISyntaxException {
        graph = LessonReader.read(Path.of(LessonReaderTest.class.getResource("/fixtures/lessons").toURI()));
    }

    private static NodeRef lesson(String id) {
        return new NodeRef("Lesson", id);
    }

    @Test
    @DisplayName("Lekcia s témou a kotvou vytvorí ABOUT, ANCHORED_TO a DESCRIBED_IN")
    void should_createLessonEdges_whenPitfallRead() {
        assertThat(graph.edges()).contains(
                new Edge("ABOUT", lesson("pitfall-02"), new NodeRef("Topic", "mvc-phases")),
                new Edge("ANCHORED_TO", lesson("pitfall-02"), new NodeRef("Endpoint", "POST /crm/blacklist/searchAction")),
                new Edge("DESCRIBED_IN", lesson("pitfall-02"), new NodeRef("Note", "07-pasce.md")));
    }

    @Test
    @DisplayName("Lekcia nesie názov, text a odkaz na zdroj v poznámkach")
    void should_setLessonProps_whenPitfallRead() {
        Node node = graph.nodes().stream().filter(n -> n.ref().equals(lesson("pitfall-02"))).findFirst().orElseThrow();

        assertThat(node.props())
                .containsEntry("title", "Action → render")
                .containsEntry("text", "Po POST musí přijít redirect.")
                .containsEntry("source", "poznamky/07-pasce.md#2");
    }

    @Test
    @DisplayName("Lekcia bez kotvy má len tému a poznámku")
    void should_haveNoAnchor_whenPitfallHasNoCounterpart() {
        assertThat(graph.edges()).filteredOn(e -> e.from().equals(lesson("pitfall-10")))
                .extracting(Edge::type).containsExactlyInAnyOrder("ABOUT", "DESCRIBED_IN");
    }

    @Test
    @DisplayName("Koncept s náhradou vytvorí REPLACED_BY a SEEN_IN")
    void should_createReplacedBy_whenConceptHasReplacement() {
        NodeRef legacy = new NodeRef("Concept", "action-mapping");

        assertThat(graph.edges()).contains(
                new Edge("REPLACED_BY", legacy, new NodeRef("Concept", "post-mapping-prg")),
                new Edge("SEEN_IN", legacy, new NodeRef("Portlet", "blacklist")),
                new Edge("DESCRIBED_IN", legacy, new NodeRef("Note", "05-mapa-konceptov.md")));
    }

    @Test
    @DisplayName("Témy sa načítajú s názvom")
    void should_createTopics_whenTopicsRead() {
        assertThat(graph.count("Topic")).isEqualTo(2);
    }

    @Test
    @DisplayName("Prázdny adresár lekcií vráti prázdny graf")
    void should_returnEmpty_whenNoLessonFiles(@TempDir Path empty) {
        Graph result = LessonReader.read(empty);

        assertThat(result.nodes()).isEmpty();
        assertThat(result.edges()).isEmpty();
    }

    @Test
    @DisplayName("Dve lekcie s rovnakým id sa odmietnu namiesto tichého prepísania")
    void should_fail_whenLessonIdDuplicated(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("pitfalls.json"), """
                [{"id":"pitfall-33","title":"A","text":"a","topics":[]},
                 {"id":"pitfall-33","title":"B","text":"b","topics":[]}]
                """);

        assertThatThrownBy(() -> LessonReader.read(dir))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pitfall-33");
    }
}
