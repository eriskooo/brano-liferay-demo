package com.example.kb.extract;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.kb.graph.Edge;
import com.example.kb.graph.Graph;
import com.example.kb.graph.GraphBuilder;
import com.example.kb.graph.Node;
import com.example.kb.graph.NodeRef;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PomExtractorTest {

    private static Graph graph;

    @BeforeAll
    static void extract() throws Exception {
        GraphBuilder builder = new GraphBuilder();
        new PomExtractor().extract(Fixtures.dir("poms"), builder);
        graph = builder.build();
    }

    private static NodeRef module(String id) {
        return new NodeRef("Module", id);
    }

    @Test
    @DisplayName("Id modulu je jeho artifactId, nie artifactId parenta")
    void should_useOwnArtifactId_whenPomHasParent() {
        assertThat(graph.contains(module("fx-child"))).isTrue();
        assertThat(graph.contains(module("fx-parent"))).isTrue();
        assertThat(graph.edges()).contains(new Edge("CHILD_OF", module("fx-child"), module("fx-parent")));
    }

    @Test
    @DisplayName("Modul má cestu s lomkami a packaging s predvolenou hodnotou jar")
    void should_setPathAndPackaging_whenModuleCreated() {
        Node child = node(module("fx-child"));
        Node other = node(module("fx-other"));

        assertThat(child.props()).containsEntry("path", "child").containsEntry("packaging", "war");
        assertThat(other.props()).containsEntry("packaging", "jar");
    }

    @Test
    @DisplayName("Závislosť na module z repa je hrana na Module, nie Library")
    void should_linkModule_whenDependencyIsRepoModule() {
        assertThat(graph.edges()).contains(new Edge("DEPENDS_ON", module("fx-child"), module("fx-other")));
        assertThat(graph.nodes()).noneMatch(n -> n.id().endsWith(":fx-other"));
    }

    @Test
    @DisplayName("Externá závislosť je Library s groupId:artifactId")
    void should_createLibrary_whenDependencyIsExternal() {
        NodeRef lib = new NodeRef("Library", "org.example:lib-a");

        assertThat(node(lib).props()).containsEntry("version", "1.0");
        assertThat(graph.edges()).contains(new Edge("DEPENDS_ON", module("fx-child"), lib));
    }

    @Test
    @DisplayName("Verzia s premennou sa neukladá")
    void should_omitVersion_whenVersionIsProperty() {
        assertThat(node(new NodeRef("Library", "org.example:lib-b")).props()).doesNotContainKey("version");
    }

    @Test
    @DisplayName("Ignoruje dependencyManagement a pluginy")
    void should_ignoreManagedAndPlugins_whenPresent() {
        assertThat(graph.contains(new NodeRef("Library", "org.example:managed"))).isFalse();
        assertThat(graph.contains(new NodeRef("Library", "org.example:plugin-dep"))).isFalse();
    }

    @Test
    @DisplayName("Preskočí target a dependency-reduced-pom.xml")
    void should_skipBuildOutput_whenPresent() {
        assertThat(graph.contains(module("fx-from-target"))).isFalse();
        assertThat(graph.contains(module("fx-reduced"))).isFalse();
        assertThat(graph.count("Module")).isEqualTo(3);
    }

    private static Node node(NodeRef ref) {
        return graph.nodes().stream().filter(n -> n.ref().equals(ref)).findFirst().orElseThrow();
    }
}
