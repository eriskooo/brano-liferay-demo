package com.example.kb.load;

import com.example.kb.extract.StructureExtractor;
import com.example.kb.graph.Graph;
import com.example.kb.graph.GraphJson;
import com.example.kb.lessons.AnchorValidator;
import com.example.kb.lessons.LessonReader;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.exceptions.AuthenticationException;
import org.neo4j.driver.exceptions.ServiceUnavailableException;

/** Príkaz load: štruktúra z kódu + lekcie → overenie kotiev → premazanie a naplnenie Neo4j. */
public final class LoadCommand {

    /** Pripojenie k Neo4j; predvolené hodnoty zodpovedajú kb-graph/docker-compose.yml. */
    public record Connection(String uri, String user, String password) {
    }

    private LoadCommand() {
    }

    /** Vráti 0 = nahraté, 1 = neplatné kotvy (DB nedotknutá), 2 = Neo4j nedostupné. */
    public static int run(Path repo, Path structureFile, Path lessonsDir, Connection connection, PrintStream out) {
        Graph structure = StructureExtractor.extract(repo);
        GraphJson.writeFile(structure, structureFile);
        Graph combined = structure.merge(LessonReader.read(lessonsDir));
        List<String> missing = AnchorValidator.missing(combined);
        if (!missing.isEmpty()) {
            out.println("Neplatné kotvy v lekciách (databáza ostala bez zmeny):");
            missing.forEach(m -> out.println("  " + m));
            return 1;
        }
        try (Driver driver = GraphDatabase.driver(connection.uri(),
                AuthTokens.basic(connection.user(), connection.password()))) {
            driver.verifyConnectivity();
            LoadResult result = new Neo4jLoader(driver).load(combined);
            out.println("Nahraté: " + result.nodes() + " uzlov, " + result.relationships() + " vzťahov");
            return 0;
        } catch (AuthenticationException e) {
            out.println("Neo4j odmietol prihlásenie na " + connection.uri() + " – skontroluj meno a heslo"
                    + " (predvolené neo4j / kbdemo123, voľby --user a --password)");
            return 2;
        } catch (ServiceUnavailableException e) {
            out.println("Neo4j nedostupné na " + connection.uri() + " (" + e.getMessage()
                    + ") – spusti kb-graph/kb.sh up");
            return 2;
        }
    }
}
