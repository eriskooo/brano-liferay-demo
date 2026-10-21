package com.example.kb.guide;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.kb.extract.StructureExtractor;
import com.example.kb.lessons.LessonReader;
import com.example.kb.load.Neo4jLoader;
import com.example.kb.load.Neo4jTestDatabase;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Každý dotaz z učebnice nad skutočným grafom – prázdny výsledok by v guide ukázal prázdne plátno. */
@Execution(ExecutionMode.SAME_THREAD)
class QueriesTest {

    private static final Path REPO = Path.of("../..").toAbsolutePath().normalize();

    @BeforeAll
    static void loadRealGraph() {
        var graph = StructureExtractor.extract(REPO).merge(LessonReader.read(REPO.resolve("kb-graph/lessons")));
        new Neo4jLoader(Neo4jTestDatabase.driver()).load(graph);
    }

    static List<SavedQuery> queries() {
        return SavedQuery.parseAll(REPO.resolve("kb-graph/queries"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("queries")
    @DisplayName("Každý uložený dotaz beží a vráti aspoň jeden riadok")
    void should_returnRows_whenEachQueryRuns(SavedQuery query) {
        try (var session = Neo4jTestDatabase.driver().session()) {
            assertThat(session.run(query.cypher()).list()).as(query.title()).isNotEmpty();
        }
    }
}
