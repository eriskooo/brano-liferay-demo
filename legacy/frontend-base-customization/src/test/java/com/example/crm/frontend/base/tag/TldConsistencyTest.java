package com.example.crm.frontend.base.tag;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.lang.reflect.Method;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

class TldConsistencyTest {

    @ParameterizedTest
    @ValueSource(strings = {"META-INF/common.tld", "META-INF/portal.tld"})
    @DisplayName("Každý tag v TLD má triedu a setter pre každý atribút (inak JSP spadne až v portáli)")
    void should_haveClassAndSetters_when_tldDeclaresTags(String tld) throws Exception {
        Document document;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(tld)) {
            assertThat(in).as(tld).isNotNull();
            document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
        }
        NodeList tags = document.getElementsByTagName("tag");
        assertThat(tags.getLength()).isPositive();
        for (int i = 0; i < tags.getLength(); i++) {
            Element tag = (Element) tags.item(i);
            Class<?> type = Class.forName(text(tag, "tag-class"));
            NodeList attributes = tag.getElementsByTagName("attribute");
            for (int j = 0; j < attributes.getLength(); j++) {
                String name = text((Element) attributes.item(j), "name");
                assertThat(hasSetter(type, name)).as(type.getSimpleName() + "." + name).isTrue();
            }
        }
    }

    private static String text(Element parent, String child) {
        return parent.getElementsByTagName(child).item(0).getTextContent().trim();
    }

    private static boolean hasSetter(Class<?> type, String attribute) {
        String setter = "set" + Character.toUpperCase(attribute.charAt(0)) + attribute.substring(1);
        for (Method method : type.getMethods()) {
            if (method.getName().equals(setter) && method.getParameterTypes().length == 1) {
                return true;
            }
        }
        return false;
    }
}
