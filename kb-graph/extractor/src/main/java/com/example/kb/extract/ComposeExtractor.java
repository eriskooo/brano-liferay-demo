package com.example.kb.extract;

import com.example.kb.graph.GraphBuilder;
import com.example.kb.graph.NodeRef;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.yaml.snakeyaml.Yaml;

/** Služby runtime z docker/docker-compose.yml: čo beží, kto na kom závisí a z ktorého modulu sa builduje. */
public class ComposeExtractor implements Extractor {

    static final String COMPOSE_FILE = "docker/docker-compose.yml";

    /**
     * Legacy WAR-y sa do Liferay hot-deployujú (build-and-deploy.sh), nie buildujú v image –
     * z compose sa to vyčítať nedá, preto explicitná mapa.
     */
    private static final Map<String, List<String>> EXTRA_BUILT_FROM =
            Map.of("liferay", List.of("customercare-portlets", "customercare-theme"));

    @Override
    public void extract(Path repoRoot, GraphBuilder graph) throws IOException {
        Path compose = repoRoot.resolve(COMPOSE_FILE);
        if (!Files.isRegularFile(compose)) {
            return;
        }
        Map<String, Object> root;
        try (Reader reader = Files.newBufferedReader(compose, StandardCharsets.UTF_8)) {
            root = new Yaml().load(reader);
        }
        Map<String, Map<String, Object>> services = cast(root.get("services"));
        services.forEach((name, def) -> addService(repoRoot, compose.getParent(), name, def, graph));
    }

    private void addService(Path repoRoot, Path composeDir, String name, Map<String, Object> def, GraphBuilder graph) {
        NodeRef self = new NodeRef("Service", name);
        Map<String, Object> props = new HashMap<>();
        if (def.get("image") instanceof String image) {
            props.put("image", image);
        }
        buildDir(def).ifPresent(dir -> props.put("build", dir));
        if (def.get("ports") instanceof List<?> ports) {
            props.put("ports", ports.stream().map(String::valueOf).collect(Collectors.joining(", ")));
        }
        if (def.get("profiles") instanceof List<?> profiles) {
            props.put("profile", profiles.stream().map(String::valueOf).collect(Collectors.joining(", ")));
        }
        graph.node("Service", name, props);
        dependsOn(def.get("depends_on")).forEach(dep -> graph.edge("DEPENDS_ON", self, new NodeRef("Service", dep)));
        buildDir(def).flatMap(dir -> moduleIn(composeDir.resolve(dir)))
                .ifPresent(module -> linkModule(self, module, graph));
        EXTRA_BUILT_FROM.getOrDefault(name, List.of()).forEach(module -> linkModule(self, module, graph));
    }

    private static void linkModule(NodeRef service, String module, GraphBuilder graph) {
        NodeRef ref = new NodeRef("Module", module);
        // Hrana len na modul, ktorý PomExtractor naozaj našiel – žiadne visiace odkazy.
        if (graph.has(ref)) {
            graph.edge("BUILT_FROM", service, ref);
        }
    }

    private static Optional<String> buildDir(Map<String, Object> def) {
        Object build = def.get("build");
        if (build instanceof String dir) {
            return Optional.of(dir);
        }
        if (build instanceof Map<?, ?> map && map.get("context") instanceof String context) {
            return Optional.of(context);
        }
        return Optional.empty();
    }

    private static Optional<String> moduleIn(Path dir) {
        Path pom = dir.resolve("pom.xml").normalize();
        if (!Files.isRegularFile(pom)) {
            return Optional.empty();
        }
        try {
            return Xml.text(Xml.parse(pom), "artifactId");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Collection<String> dependsOn(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        if (value instanceof Map<?, ?> map) {
            return map.keySet().stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private static <T> T cast(Object value) {
        return (T) value;
    }
}
