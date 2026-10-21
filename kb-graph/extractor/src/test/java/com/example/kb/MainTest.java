package com.example.kb;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MainTest {

    private static final String REPO = Path.of("../..").toAbsolutePath().normalize().toString();

    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private final PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);

    private String output() {
        return buffer.toString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("extract zapíše structure.json a check ho potom uzná za aktuálny")
    void should_returnZero_whenStructureFresh(@TempDir Path tmp) {
        String file = tmp.resolve("structure.json").toString();

        assertThat(Main.run(new String[] {"extract", "--repo", REPO, "--out", file}, out)).isZero();
        assertThat(Main.run(new String[] {"check", "--repo", REPO, "--out", file}, out)).isZero();
    }

    @Test
    @DisplayName("check vráti 1, keď sa structure.json líši")
    void should_returnOne_whenStructureStale(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("structure.json");
        Files.writeString(file, "{\"nodes\":[],\"edges\":[]}\n");

        int code = Main.run(new String[] {"check", "--repo", REPO, "--out", file.toString()}, out);

        assertThat(code).isEqualTo(1);
        assertThat(output()).contains("zastaraný");
    }

    @Test
    @DisplayName("check vráti 1, keď structure.json neexistuje")
    void should_returnOne_whenStructureMissing(@TempDir Path tmp) {
        String file = tmp.resolve("missing.json").toString();

        assertThat(Main.run(new String[] {"check", "--repo", REPO, "--out", file}, out)).isEqualTo(1);
    }

    @Test
    @DisplayName("Neznámy príkaz vráti 64 a vypíše použitie")
    void should_return64_whenUnknownCommand() {
        assertThat(Main.run(new String[] {"nieco"}, out)).isEqualTo(64);
        assertThat(output()).contains("Použitie");
    }

    @Test
    @DisplayName("Bez príkazu vráti 64")
    void should_return64_whenNoArguments() {
        assertThat(Main.run(new String[] {}, out)).isEqualTo(64);
    }

    @Test
    @DisplayName("Chybný JSON lekcií vypíše krátku chybu namiesto stack trace")
    void should_printError_whenLessonsJsonMalformed(@TempDir Path tmp) throws Exception {
        Path lessons = Files.createDirectories(tmp.resolve("lessons"));
        Files.writeString(lessons.resolve("pitfalls.json"), "[{\"id\":\"pitfall-01\",");

        int code = Main.run(new String[] {"load", "--repo", REPO, "--lessons", lessons.toString(),
                "--out", tmp.resolve("structure.json").toString()}, out);

        assertThat(code).isEqualTo(1);
        assertThat(output()).contains("Chyba").contains("pitfalls.json");
    }

    @Test
    @DisplayName("Neplatná adresa Neo4j vypíše krátku chybu namiesto stack trace")
    void should_printError_whenUriMalformed(@TempDir Path tmp) {
        int code = Main.run(new String[] {"load", "--repo", REPO, "--out", tmp.resolve("structure.json").toString(),
                "--uri", "http://localhost:7474"}, out);

        assertThat(code).isEqualTo(1);
        assertThat(output()).contains("Chyba");
    }
}
