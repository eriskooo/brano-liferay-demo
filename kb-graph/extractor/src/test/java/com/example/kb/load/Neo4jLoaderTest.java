package com.example.kb.load;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.kb.Main;
import com.example.kb.graph.Graph;
import com.example.kb.graph.GraphBuilder;
import com.example.kb.graph.NodeRef;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Neo4jLoaderTest {

    private static final String REPO = Path.of("../..").toAbsolutePath().normalize().toString();

    private static Graph threeNodes() {
        return new GraphBuilder()
                .node("Service", "redis", Map.of("image", "redis:7.4-alpine"))
                .node("Service", "crm-web", Map.of())
                .node("Module", "crm-web", Map.of("packaging", "war"))
                .edge("DEPENDS_ON", new NodeRef("Service", "crm-web"), new NodeRef("Service", "redis"))
                .edge("BUILT_FROM", new NodeRef("Service", "crm-web"), new NodeRef("Module", "crm-web"))
                .build();
    }

    private static Graph twoNodes() {
        return new GraphBuilder()
                .node("Service", "redis", Map.of())
                .node("Service", "crm-web", Map.of())
                .edge("DEPENDS_ON", new NodeRef("Service", "crm-web"), new NodeRef("Service", "redis"))
                .build();
    }

    private static Neo4jLoader loader() {
        return new Neo4jLoader(Neo4jTestDatabase.driver());
    }

    @Test
    @DisplayName("Nahratie vytvorí uzly s labelom, vlastnosťami a vzťahy")
    void should_createNodesAndRelationships_whenLoaded() {
        LoadResult result = loader().load(threeNodes());

        assertThat(result).isEqualTo(new LoadResult(3, 2));
        assertThat(Neo4jTestDatabase.count(
                "MATCH (s:Service {id:'redis', image:'redis:7.4-alpine'}) RETURN count(s)")).isEqualTo(1);
        assertThat(Neo4jTestDatabase.count(
                "MATCH (:Service {id:'crm-web'})-[r:BUILT_FROM]->(:Module {id:'crm-web'}) RETURN count(r)")).isEqualTo(1);
    }

    @Test
    @DisplayName("Opakované nahratie nezdvojí graf")
    void should_notDuplicate_whenLoadedTwice() {
        loader().load(threeNodes());

        assertThat(loader().load(threeNodes())).isEqualTo(new LoadResult(3, 2));
    }

    @Test
    @DisplayName("Nahratie zmaže uzly, ktoré v JSON nie sú")
    void should_removeStaleNodes_whenGraphShrinks() {
        loader().load(threeNodes());

        assertThat(loader().load(twoNodes())).isEqualTo(new LoadResult(2, 1));
    }

    @Test
    @DisplayName("Neplatná kotva zastaví load a DB ostane nezmenená")
    void should_keepDatabase_whenAnchorsInvalid(@TempDir Path tmp) throws Exception {
        loader().load(threeNodes());
        Path lessons = Files.createDirectories(tmp.resolve("lessons"));
        Files.writeString(lessons.resolve("pitfalls.json"), """
                [{"id":"pitfall-01","title":"x","text":"x","topics":[],
                  "anchors":[{"label":"Service","id":"neexistuje"}],"note":"07-pasce.md"}]
                """);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        int code = Main.run(new String[] {"load", "--repo", REPO, "--lessons", lessons.toString(),
                "--out", tmp.resolve("structure.json").toString(), "--uri", Neo4jTestDatabase.boltUrl(),
                "--password", Neo4jTestDatabase.PASSWORD}, new PrintStream(buffer, true, StandardCharsets.UTF_8));

        assertThat(code).isEqualTo(1);
        assertThat(buffer.toString(StandardCharsets.UTF_8)).contains("pitfall-01 -[ANCHORED_TO]-> Service:neexistuje");
        assertThat(Neo4jTestDatabase.count("MATCH (n) RETURN count(n)")).isEqualTo(3);
    }

    @Test
    @DisplayName("load nahrá skutočné repo vrátane lekcií")
    void should_loadRealRepo_whenLessonsValid(@TempDir Path tmp) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        int code = Main.run(new String[] {"load", "--repo", REPO, "--out", tmp.resolve("structure.json").toString(),
                "--uri", Neo4jTestDatabase.boltUrl(), "--password", Neo4jTestDatabase.PASSWORD},
                new PrintStream(buffer, true, StandardCharsets.UTF_8));

        assertThat(code).as(buffer.toString(StandardCharsets.UTF_8)).isZero();
        assertThat(Neo4jTestDatabase.count("MATCH (l:Lesson)-[:ANCHORED_TO]->(:Service {id:'redis'}) RETURN count(l)"))
                .isPositive();
    }

    @Test
    @DisplayName("Nedostupné Neo4j vráti 2 so zrozumiteľnou hláškou")
    void should_return2_whenNeo4jUnavailable(@TempDir Path tmp) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        int code = Main.run(new String[] {"load", "--repo", REPO, "--out", tmp.resolve("structure.json").toString(),
                "--uri", "bolt://localhost:1"}, new PrintStream(buffer, true, StandardCharsets.UTF_8));

        assertThat(code).isEqualTo(2);
        assertThat(buffer.toString(StandardCharsets.UTF_8)).contains("kb.sh up");
    }

    @Test
    @DisplayName("Zlé heslo vráti 2 s hláškou o prihlásení, nie o štarte Neo4j")
    void should_return2_whenPasswordWrong(@TempDir Path tmp) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        int code = Main.run(new String[] {"load", "--repo", REPO, "--out", tmp.resolve("structure.json").toString(),
                "--uri", Neo4jTestDatabase.boltUrl(), "--password", "zle-heslo"},
                new PrintStream(buffer, true, StandardCharsets.UTF_8));

        assertThat(code).isEqualTo(2);
        assertThat(buffer.toString(StandardCharsets.UTF_8)).contains("heslo").doesNotContain("kb.sh up");
    }
}
