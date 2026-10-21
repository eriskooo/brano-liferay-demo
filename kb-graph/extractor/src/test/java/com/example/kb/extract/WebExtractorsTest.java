package com.example.kb.extract;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.kb.graph.Edge;
import com.example.kb.graph.Graph;
import com.example.kb.graph.GraphBuilder;
import com.example.kb.graph.Node;
import com.example.kb.graph.NodeRef;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Portlety, endpointy a tabuľky – fixtures napodobňujú rozloženie skutočného repa. */
class WebExtractorsTest {

    private static Graph graph;

    @BeforeAll
    static void extract() throws Exception {
        GraphBuilder builder = new GraphBuilder();
        var root = Fixtures.dir("web");
        new PortletExtractor().extract(root, builder);
        new EndpointExtractor().extract(root, builder);
        new SqlExtractor().extract(root, builder);
        graph = builder.build();
    }

    private static NodeRef endpoint(String id) {
        return new NodeRef("Endpoint", id);
    }

    private static Node node(NodeRef ref) {
        return graph.nodes().stream().filter(n -> n.ref().equals(ref)).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("Portlet z portlet.xml patrí modulu s najbližším pom.xml")
    void should_declarePortlet_whenPortletXmlFound() {
        NodeRef blacklist = new NodeRef("Portlet", "blacklist");

        assertThat(graph.count("Portlet")).isEqualTo(2);
        assertThat(node(blacklist).props()).containsEntry("class", "com.example.DispatcherPortletCustom");
        assertThat(graph.edges()).contains(
                new Edge("DECLARES", new NodeRef("Module", "customercare-portlets"), blacklist));
    }

    @Test
    @DisplayName("Endpoint skladá context-path, prefix triedy a cestu metódy")
    void should_composePath_whenClassHasRequestMapping() {
        assertThat(graph.contains(endpoint("GET /crm/blacklist/search"))).isTrue();
        assertThat(graph.contains(endpoint("POST /crm/blacklist/searchAction"))).isTrue();
        assertThat(node(endpoint("GET /crm/blacklist/search")).props())
                .containsEntry("controller", "WithPrefixController");
    }

    @Test
    @DisplayName("Zoznam ciest v anotácii vytvorí viac endpointov")
    void should_createEachPath_whenMappingHasArray() {
        assertThat(graph.contains(endpoint("GET /crm/"))).isTrue();
        assertThat(graph.contains(endpoint("GET /crm/blacklist"))).isTrue();
        assertThat(graph.count("Endpoint")).isEqualTo(4);
    }

    @Test
    @DisplayName("Endpoint patrí modulu crm-web a odkazuje na súbor s relatívnou cestou")
    void should_linkFile_whenEndpointCreated() {
        NodeRef file = new NodeRef("File", "crm-web/src/main/java/com/example/crm/web/HomeController.java");

        assertThat(graph.contains(file)).isTrue();
        assertThat(graph.edges()).contains(
                new Edge("DEFINED_IN", endpoint("GET /crm/"), file),
                new Edge("DECLARES", new NodeRef("Module", "crm-web"), endpoint("GET /crm/")));
    }

    @Test
    @DisplayName("Tabuľky sa načítajú so schémou, schéma a rola nie")
    void should_createTables_whenCreateTableFound() {
        NodeRef record = new NodeRef("Table", "blacklist.blacklist_record");

        assertThat(graph.count("Table")).isEqualTo(2);
        assertThat(graph.edges()).contains(new Edge("OWNS", new NodeRef("Service", "postgres"), record));
        assertThat(graph.nodes()).noneMatch(n -> n.id().contains("crm_app"));
    }
}
