package com.example.kb.extract;

import com.example.kb.graph.GraphBuilder;
import com.example.kb.graph.NodeRef;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Tabuľky zdieľanej DB z DDL v docker/postgres/init – tú istú schému používa stará aj nová appka. */
public class SqlExtractor implements Extractor {

    static final String INIT_DIR = "docker/postgres/init";

    private static final Pattern CREATE_TABLE = Pattern.compile("CREATE\\s+TABLE\\s+([\\w.]+)", Pattern.CASE_INSENSITIVE);

    @Override
    public void extract(Path repoRoot, GraphBuilder graph) throws IOException {
        Path dir = repoRoot.resolve(INIT_DIR);
        if (!Files.isDirectory(dir)) {
            return;
        }
        NodeRef postgres = new NodeRef("Service", "postgres");
        // Len DDL (01-*); seed (02-*) tabuľky nevytvára.
        for (Path file : RepoFiles.find(dir, p -> p.getFileName().toString().matches("01-.*\\.sql"))) {
            Matcher m = CREATE_TABLE.matcher(Files.readString(file, StandardCharsets.UTF_8));
            while (m.find()) {
                String id = m.group(1).toLowerCase(Locale.ROOT);
                graph.node("Table", id, Map.of());
                graph.edge("OWNS", postgres, new NodeRef("Table", id));
            }
        }
    }
}
