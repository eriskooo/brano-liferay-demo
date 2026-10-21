package com.example.kb.extract;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/** Pomôcky nad JDK DOM – bez namespace, POM aj portlet.xml čítame len podľa názvov elementov. */
final class Xml {

    private Xml() {
    }

    static Element parse(Path file) throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            return factory.newDocumentBuilder().parse(file.toFile()).getDocumentElement();
        } catch (ParserConfigurationException | SAXException e) {
            throw new IOException("Neplatné XML: " + file, e);
        }
    }

    /** Priame deti s daným názvom (nie hlbší potomkovia). */
    static List<Element> children(Element parent, String name) {
        List<Element> result = new ArrayList<>();
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e && e.getTagName().equals(name)) {
                result.add(e);
            }
        }
        return result;
    }

    static Optional<Element> child(Element parent, String name) {
        return children(parent, name).stream().findFirst();
    }

    static Optional<String> text(Element parent, String name) {
        return child(parent, name).map(e -> e.getTextContent().trim());
    }
}
