package com.example.kb.extract;

import com.example.kb.graph.GraphBuilder;
import com.example.kb.graph.NodeRef;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.w3c.dom.Element;

/** Portlety legacy appky z WEB-INF/portlet.xml – „pred“ strana migrácie. */
public class PortletExtractor implements Extractor {

    @Override
    public void extract(Path repoRoot, GraphBuilder graph) throws IOException {
        for (Path file : RepoFiles.find(repoRoot, PortletExtractor::isPortletXml)) {
            Optional<String> module = owningModule(repoRoot, file.getParent());
            for (Element portlet : Xml.children(Xml.parse(file), "portlet")) {
                addPortlet(portlet, module, graph);
            }
        }
    }

    private static void addPortlet(Element portlet, Optional<String> module, GraphBuilder graph) {
        Optional<String> name = Xml.text(portlet, "portlet-name");
        if (name.isEmpty()) {
            return;
        }
        Map<String, Object> props = new HashMap<>();
        Xml.text(portlet, "portlet-class").ifPresent(c -> props.put("class", c));
        graph.node("Portlet", name.get(), props);
        module.ifPresent(m -> graph.edge("DECLARES", new NodeRef("Module", m), new NodeRef("Portlet", name.get())));
    }

    private static boolean isPortletXml(Path p) {
        return p.getFileName().toString().equals("portlet.xml")
                && p.getParent() != null && p.getParent().getFileName().toString().equals("WEB-INF");
    }

    /** Modul = najbližší nadradený adresár s pom.xml (portlet.xml leží hlboko v src/main/webapp). */
    static Optional<String> owningModule(Path repoRoot, Path dir) throws IOException {
        Path root = repoRoot.toAbsolutePath().normalize();
        for (Path d = dir.toAbsolutePath().normalize(); d != null && d.startsWith(root); d = d.getParent()) {
            Path pom = d.resolve("pom.xml");
            if (Files.isRegularFile(pom)) {
                return Xml.text(Xml.parse(pom), "artifactId");
            }
        }
        return Optional.empty();
    }
}
