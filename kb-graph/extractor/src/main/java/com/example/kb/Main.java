package com.example.kb;

import com.example.kb.extract.StructureExtractor;
import com.example.kb.graph.GraphJson;
import com.example.kb.guide.GuideGenerator;
import com.example.kb.guide.SavedQuery;
import com.example.kb.load.LoadCommand;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Príkazový riadok znalostnej bázy; volá ho kb-graph/kb.sh (a kb.ps1). */
public final class Main {

    static final String STRUCTURE_FILE = "kb-graph/graph/structure.json";
    static final String LESSONS_DIR = "kb-graph/lessons";
    static final String QUERIES_DIR = "kb-graph/queries";
    static final String GUIDE_FILE = "kb-graph/guide/kb.html";
    private static final int USAGE = 64;
    private static final String HELP = """
            Použitie: java -jar kb-graph-extractor.jar <príkaz> [voľby]
              extract  vygeneruje kb-graph/graph/structure.json z kódu repa
              check    overí, že commitnutý structure.json zodpovedá kódu (exit 1, ak nie)
              load     extract + lekcie, overí kotvy a premaže a naplní Neo4j
              guide    vygeneruje kb-graph/guide/kb.html z kb-graph/queries/*.cypher
            Voľby: --repo <cesta> --out <structure.json> --lessons <adresár>
                   --uri bolt://localhost:7687 --user neo4j --password kbdemo123
            """;

    private Main() {
    }

    public static void main(String[] args) {
        System.exit(run(args, System.out));
    }

    /** Spustí príkaz a vráti exit kód – bez System.exit, aby sa dal testovať. */
    public static int run(String[] args, PrintStream out) {
        if (args.length == 0) {
            out.print(HELP);
            return USAGE;
        }
        Map<String, String> options = options(args);
        Path repo = Path.of(options.getOrDefault("repo", ".")).toAbsolutePath().normalize();
        Path structure = options.containsKey("out") ? Path.of(options.get("out")) : repo.resolve(STRUCTURE_FILE);
        try {
            return switch (args[0]) {
                case "extract" -> extract(repo, structure, out);
                case "check" -> check(repo, structure, out);
                case "load" -> LoadCommand.run(repo, structure,
                        Path.of(options.getOrDefault("lessons", repo.resolve(LESSONS_DIR).toString())),
                        new LoadCommand.Connection(options.getOrDefault("uri", "bolt://localhost:7687"),
                                options.getOrDefault("user", "neo4j"), options.getOrDefault("password", "kbdemo123")),
                        out);
                case "guide" -> guide(repo, out);
                default -> {
                    out.print(HELP);
                    yield USAGE;
                }
            };
        } catch (IllegalStateException | IllegalArgumentException | UncheckedIOException e) {
            // Chyba v ručne upravenom vstupe (lekcie, POM, --uri) – krátka hláška namiesto stack trace.
            out.println("Chyba: " + e.getMessage());
            return 1;
        }
    }

    private static int extract(Path repo, Path structure, PrintStream out) {
        var graph = StructureExtractor.extract(repo);
        GraphJson.writeFile(graph, structure);
        out.println("Zapísané " + structure + ": " + graph.nodes().size() + " uzlov, " + graph.edges().size() + " hrán");
        return 0;
    }

    private static int guide(Path repo, PrintStream out) {
        var queries = SavedQuery.parseAll(repo.resolve(QUERIES_DIR));
        Path file = repo.resolve(GUIDE_FILE);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GuideGenerator.render(queries), StandardCharsets.UTF_8);
        } catch (IOException e) {
            out.println("Chyba: guide sa nedá zapísať – " + e.getMessage());
            return 1;
        }
        out.println("Zapísané " + file + ": " + queries.size() + " dotazov");
        return 0;
    }

    private static int check(Path repo, Path structure, PrintStream out) {
        String expected = GraphJson.write(StructureExtractor.extract(repo));
        String actual;
        try {
            actual = Files.isRegularFile(structure) ? Files.readString(structure, StandardCharsets.UTF_8) : "";
        } catch (IOException e) {
            actual = "";
        }
        // Porovnanie bez ohľadu na CRLF – git na Windows môže pri checkoute zmeniť konce riadkov.
        if (expected.equals(actual.replace("\r\n", "\n"))) {
            out.println("structure.json je aktuálny");
            return 0;
        }
        out.println("structure.json je zastaraný – spusti kb-graph/kb.sh extract");
        return 1;
    }

    private static Map<String, String> options(String[] args) {
        Map<String, String> options = new HashMap<>();
        for (int i = 1; i + 1 < args.length; i += 2) {
            if (args[i].startsWith("--")) {
                options.put(args[i].substring(2), args[i + 1]);
            }
        }
        return options;
    }
}
