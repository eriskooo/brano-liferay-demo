package com.example.kb.extract;

import com.example.kb.graph.GraphBuilder;
import com.example.kb.graph.NodeRef;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTTP endpointy zmigrovanej appky z anotácií controllerov – „po“ strana migrácie.
 * Text, nie kompilátor (ako v článku): stačí na @GetMapping/@PostMapping s literálmi.
 */
public class EndpointExtractor implements Extractor {

    static final String SOURCES = "crm-web/src/main/java";
    static final String PROPERTIES = "crm-web/src/main/resources/application.properties";

    private static final Pattern CLASS_PREFIX = Pattern.compile("@RequestMapping\\(\"([^\"]*)\"\\)");
    private static final Pattern CLASS_KEYWORD = Pattern.compile("\\bclass\\s+\\w+");
    private static final Pattern METHOD_MAPPING = Pattern.compile("@(Get|Post)Mapping\\((\\{[^}]*}|\"[^\"]*\")\\)");
    private static final Pattern QUOTED = Pattern.compile("\"([^\"]*)\"");

    @Override
    public void extract(Path repoRoot, GraphBuilder graph) throws IOException {
        Path sources = repoRoot.resolve(SOURCES);
        if (!Files.isDirectory(sources)) {
            return;
        }
        String contextPath = contextPath(repoRoot);
        Optional<String> module = PortletExtractor.owningModule(repoRoot, sources);
        for (Path file : RepoFiles.find(sources, p -> p.toString().endsWith(".java"))) {
            addEndpoints(repoRoot, file, contextPath, module, graph);
        }
    }

    private void addEndpoints(Path repoRoot, Path file, String contextPath, Optional<String> module,
                              GraphBuilder graph) throws IOException {
        String source = Files.readString(file, StandardCharsets.UTF_8);
        // Prefix triedy = @RequestMapping pred deklaráciou triedy (medzi nimi môžu byť ďalšie anotácie).
        Matcher classKeyword = CLASS_KEYWORD.matcher(source);
        String header = classKeyword.find() ? source.substring(0, classKeyword.start()) : "";
        Matcher prefix = CLASS_PREFIX.matcher(header);
        String classPrefix = prefix.find() ? prefix.group(1) : "";
        String controller = file.getFileName().toString().replace(".java", "");
        NodeRef fileRef = new NodeRef("File", RepoFiles.rel(repoRoot, file));
        Matcher m = METHOD_MAPPING.matcher(source);
        while (m.find()) {
            String method = m.group(1).toUpperCase();
            for (String path : paths(m.group(2))) {
                String id = method + " " + contextPath + classPrefix + path;
                graph.node("Endpoint", id, Map.of("controller", controller));
                graph.node("File", fileRef.id(), Map.of());
                graph.edge("DEFINED_IN", new NodeRef("Endpoint", id), fileRef);
                module.ifPresent(mod -> graph.edge("DECLARES", new NodeRef("Module", mod), new NodeRef("Endpoint", id)));
            }
        }
    }

    private static List<String> paths(String annotationValue) {
        return QUOTED.matcher(annotationValue).results().map(r -> r.group(1)).toList();
    }

    private static String contextPath(Path repoRoot) throws IOException {
        Path file = repoRoot.resolve(PROPERTIES);
        if (!Files.isRegularFile(file)) {
            return "";
        }
        Properties props = new Properties();
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            props.load(reader);
        }
        return props.getProperty("server.servlet.context-path", "");
    }
}
