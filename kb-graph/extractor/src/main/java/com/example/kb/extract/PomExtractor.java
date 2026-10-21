package com.example.kb.extract;

import com.example.kb.graph.GraphBuilder;
import com.example.kb.graph.NodeRef;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.w3c.dom.Element;

/**
 * Moduly a závislosti z pom.xml. Pasca z článku: artifactId sa číta len z priamych detí project –
 * prvý artifactId v súbore patrí parentovi a všetky moduly by sa zliali do jedného uzla.
 */
public class PomExtractor implements Extractor {

    private static final Map<String, String> APP_BY_TOP_DIR = Map.of(
            "legacy", "legacy", "crm-web", "crm-web", "sso-mock", "sso-mock", "e2e", "e2e", "kb-graph", "kb-graph");

    private record Pom(Path dir, Element project, String artifactId) {
    }

    @Override
    public void extract(Path repoRoot, GraphBuilder graph) throws IOException {
        List<Path> files = RepoFiles.find(repoRoot, p -> p.getFileName().toString().equals("pom.xml"));
        Map<String, Pom> modules = new LinkedHashMap<>();
        for (Path file : files) {
            Element project = Xml.parse(file);
            Xml.text(project, "artifactId").ifPresent(id -> modules.put(id, new Pom(file.getParent(), project, id)));
        }
        modules.values().forEach(pom -> addModule(repoRoot, pom, graph));
        modules.values().forEach(pom -> addEdges(pom, modules, graph));
    }

    private void addModule(Path repoRoot, Pom pom, GraphBuilder graph) {
        String path = RepoFiles.rel(repoRoot, pom.dir());
        Map<String, Object> props = new HashMap<>();
        props.put("path", path.isEmpty() ? "." : path);
        props.put("packaging", Xml.text(pom.project(), "packaging").orElse("jar"));
        appOf(path).ifPresent(app -> props.put("app", app));
        graph.node("Module", pom.artifactId(), props);
    }

    private void addEdges(Pom pom, Map<String, Pom> modules, GraphBuilder graph) {
        NodeRef self = new NodeRef("Module", pom.artifactId());
        Xml.child(pom.project(), "parent")
                .flatMap(parent -> Xml.text(parent, "artifactId"))
                .filter(modules::containsKey)
                .ifPresent(parentId -> graph.edge("CHILD_OF", self, new NodeRef("Module", parentId)));
        // Len project/dependencies – dependencyManagement a závislosti pluginov nie sú závislosti modulu.
        Xml.child(pom.project(), "dependencies").stream()
                .flatMap(deps -> Xml.children(deps, "dependency").stream())
                .forEach(dep -> addDependency(self, dep, modules, graph));
    }

    private void addDependency(NodeRef self, Element dep, Map<String, Pom> modules, GraphBuilder graph) {
        String artifactId = Xml.text(dep, "artifactId").orElse("");
        // Súrodenca v reaktore nespoznáme podľa groupId (býva ${project.groupId}), ale podľa artifactId z repa.
        if (modules.containsKey(artifactId)) {
            graph.edge("DEPENDS_ON", self, new NodeRef("Module", artifactId));
            return;
        }
        String id = Xml.text(dep, "groupId").orElse("") + ":" + artifactId;
        Map<String, Object> props = new HashMap<>();
        Xml.text(dep, "version").filter(v -> !v.contains("${")).ifPresent(v -> props.put("version", v));
        graph.node("Library", id, props);
        graph.edge("DEPENDS_ON", self, new NodeRef("Library", id));
    }

    private static Optional<String> appOf(String path) {
        String top = path.contains("/") ? path.substring(0, path.indexOf('/')) : path;
        return Optional.ofNullable(APP_BY_TOP_DIR.get(top));
    }
}
