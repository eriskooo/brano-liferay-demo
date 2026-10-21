package com.example.kb.extract;

import com.example.kb.graph.GraphBuilder;
import com.example.kb.graph.NodeRef;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Súbory znalostnej bázy poznamky/*.md a odkazy medzi nimi – kostra, na ktorú sa vešajú lekcie. */
public class NoteExtractor implements Extractor {

    static final String NOTES_DIR = "poznamky";

    /** Len odkazy na súrodencov v tom istom adresári; kotva (#…) sa odreže. */
    private static final Pattern LINK = Pattern.compile("]\\(([\\w.-]+\\.md)(?:#[^)]*)?\\)");

    @Override
    public void extract(Path repoRoot, GraphBuilder graph) throws IOException {
        Path dir = repoRoot.resolve(NOTES_DIR);
        if (!Files.isDirectory(dir)) {
            return;
        }
        List<Path> files = RepoFiles.find(dir, p -> p.getParent().equals(dir) && p.toString().endsWith(".md"));
        Set<String> names = files.stream().map(p -> p.getFileName().toString()).collect(Collectors.toSet());
        for (Path file : files) {
            addNote(file, names, graph);
        }
    }

    private void addNote(Path file, Set<String> names, GraphBuilder graph) throws IOException {
        String name = file.getFileName().toString();
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        String title = lines.stream().filter(l -> l.startsWith("# ")).findFirst()
                .map(l -> l.substring(2).trim()).orElse(name);
        graph.node("Note", name, Map.of("title", title));
        NodeRef self = new NodeRef("Note", name);
        LINK.matcher(String.join("\n", lines)).results()
                .map(r -> r.group(1))
                .filter(target -> names.contains(target) && !target.equals(name))
                .forEach(target -> graph.edge("LINKS_TO", self, new NodeRef("Note", target)));
    }
}
