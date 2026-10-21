package com.example.kb.guide;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GuideGeneratorTest {

    private static final Path REPO = Path.of("../..").toAbsolutePath().normalize();
    private static final Path QUERIES = REPO.resolve("kb-graph/queries");

    @Test
    @DisplayName("Uložený dotaz sa rozdelí na názov, pojem, text, SQL a Cypher")
    void should_parseHeader_whenCypherFileRead(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("01-x.cypher");
        Files.writeString(file, """
                // title: Kto závisí na Redis
                // teaches: premenlivá dĺžka cesty
                // Prvý riadok textu.
                // Druhý riadok textu.
                // sql: WITH RECURSIVE x AS (SELECT 1)
                MATCH (s:Service)-[:DEPENDS_ON*]->(:Service {id:'redis'})
                RETURN s
                """, StandardCharsets.UTF_8);

        SavedQuery q = SavedQuery.parse(file);

        assertThat(q.title()).isEqualTo("Kto závisí na Redis");
        assertThat(q.teaches()).isEqualTo("premenlivá dĺžka cesty");
        assertThat(q.text()).isEqualTo("Prvý riadok textu. Druhý riadok textu.");
        assertThat(q.sql()).isEqualTo("WITH RECURSIVE x AS (SELECT 1)");
        assertThat(q.cypher()).isEqualTo("MATCH (s:Service)-[:DEPENDS_ON*]->(:Service {id:'redis'})\nRETURN s");
    }

    @Test
    @DisplayName("Špeciálne znaky v dotaze sa escapujú")
    void should_escapeHtml_whenQueryHasArrows() {
        String html = GuideGenerator.render(List.of(
                new SavedQuery("t", "p", "a < b", "", "MATCH (a)-[:X]->(b)<-[:Y]-(c) RETURN a")));

        assertThat(html).contains("-[:X]-&gt;(b)&lt;-[:Y]-").contains("a &lt; b").doesNotContain("->(b)");
    }

    @Test
    @DisplayName("Guide obsahuje úvodný slajd a slajd pre každý dotaz")
    void should_containAllQueries_whenRendered() {
        List<SavedQuery> queries = SavedQuery.parseAll(QUERIES);
        String html = GuideGenerator.render(queries);

        assertThat(queries).hasSize(11);
        assertThat(html.split("<slide", -1)).hasSize(queries.size() + 2);
        queries.forEach(q -> assertThat(html).contains(GuideGenerator.escape(q.cypher())));
    }

    @Test
    @DisplayName("Commitnutý kb.html zodpovedá dotazom")
    void should_matchCommittedGuide_whenRegenerated() throws Exception {
        String committed = Files.readString(REPO.resolve("kb-graph/guide/kb.html"), StandardCharsets.UTF_8);

        assertThat(committed.replace("\r\n", "\n")).isEqualTo(GuideGenerator.render(SavedQuery.parseAll(QUERIES)));
    }

    @Test
    @DisplayName("Prázdny adresár dotazov dá guide len s úvodom")
    void should_renderIntroOnly_whenNoQueries(@TempDir Path empty) {
        assertThat(GuideGenerator.render(SavedQuery.parseAll(empty)).split("<slide", -1)).hasSize(2);
    }
}
