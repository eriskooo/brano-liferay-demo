package com.example.kb.extract;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.kb.graph.Edge;
import com.example.kb.graph.Graph;
import com.example.kb.graph.GraphBuilder;
import com.example.kb.graph.NodeRef;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NoteExtractorTest {

    private static Graph graph;

    @BeforeAll
    static void extract() throws Exception {
        GraphBuilder builder = new GraphBuilder();
        new NoteExtractor().extract(Fixtures.dir("notes"), builder);
        graph = builder.build();
    }

    private static NodeRef note(String id) {
        return new NodeRef("Note", id);
    }

    @Test
    @DisplayName("Odkaz medzi poznámkami vytvorí LINKS_TO bez kotvy")
    void should_linkNotes_whenMarkdownLinkFound() {
        assertThat(graph.edges()).contains(
                new Edge("LINKS_TO", note("a.md"), note("07-pasce.md")),
                new Edge("LINKS_TO", note("07-pasce.md"), note("a.md")));
    }

    @Test
    @DisplayName("Odkaz mimo poznámok a odkaz na seba hranu nevytvorí")
    void should_skipForeignAndSelfLinks_whenPresent() {
        assertThat(graph.edges()).hasSize(2);
    }

    @Test
    @DisplayName("Názov poznámky je prvý nadpis prvej úrovne")
    void should_useFirstHeading_whenTitleRead() {
        assertThat(graph.nodes()).filteredOn(n -> n.ref().equals(note("07-pasce.md")))
                .singleElement().satisfies(n -> assertThat(n.props()).containsEntry("title", "Pasce"));
    }

    @Test
    @DisplayName("Lokálne súbory LOCAL-* sa ignorujú")
    void should_skipLocalFiles_whenPresent() {
        assertThat(graph.count("Note")).isEqualTo(2);
        assertThat(graph.contains(note("LOCAL-aliasy.md"))).isFalse();
    }
}
