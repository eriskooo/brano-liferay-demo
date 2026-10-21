package com.example.kb.lessons;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.kb.extract.StructureExtractor;
import com.example.kb.graph.Graph;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Skutočné lekcie z kb-graph/lessons proti skutočnej štruktúre – zastaraná kotva tu zlyhá skôr ako pri load. */
class RealLessonsTest {

    private static final Path REPO = Path.of("../..").toAbsolutePath().normalize();
    private static Graph structure;
    private static Graph lessons;

    @BeforeAll
    static void read() {
        structure = StructureExtractor.extract(REPO);
        lessons = LessonReader.read(REPO.resolve("kb-graph/lessons"));
    }

    @Test
    @DisplayName("Skutočné lekcie sedia na skutočnú štruktúru")
    void should_haveNoMissingAnchors_whenRealLessonsLoaded() {
        assertThat(AnchorValidator.missing(structure.merge(lessons))).isEmpty();
    }

    @Test
    @DisplayName("Každá pasca z poznamky/07 má svoju lekciu")
    void should_coverEveryPitfall_whenCountedInNotes() throws Exception {
        long numbered = Files.readAllLines(REPO.resolve("poznamky/07-pasce.md"), StandardCharsets.UTF_8).stream()
                .filter(l -> l.matches("^\\d+\\. .*"))
                .count();

        assertThat(numbered).isGreaterThanOrEqualTo(32);
        assertThat(lessons.count("Lesson")).isEqualTo(numbered);
    }

    @Test
    @DisplayName("Každá lekcia patrí aspoň do jednej témy")
    void should_haveTopic_whenLessonLoaded() {
        lessons.nodes().stream().filter(n -> n.label().equals("Lesson")).forEach(lesson ->
                assertThat(lessons.edges()).as(lesson.id())
                        .anyMatch(e -> e.type().equals("ABOUT") && e.from().equals(lesson.ref())));
    }

    @Test
    @DisplayName("Aspoň tri legacy koncepty nemajú výskyt v deme")
    void should_haveUnseenLegacyConcepts_whenConceptsLoaded() {
        long unseen = lessons.nodes().stream()
                .filter(n -> n.label().equals("Concept") && "legacy".equals(n.props().get("side")))
                .filter(n -> lessons.edges().stream().noneMatch(e -> e.type().equals("SEEN_IN") && e.from().equals(n.ref())))
                .count();

        assertThat(unseen).isGreaterThanOrEqualTo(3);
    }
}
