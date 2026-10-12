package com.example.crm.frontend;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Každý pevný kľúč z JSP ({@code key="…"} bez EL) musí byť v messages.properties – preklep inak uvidí až používateľ. */
class MessageKeysTest {

    private static final Pattern KEY = Pattern.compile("(?:key|code)=\"([a-zA-Z0-9_.]+)\"");

    @Test
    @DisplayName("Všetky i18n kľúče použité v JSP existujú")
    void should_defineEveryKey_when_usedInJsp() throws IOException {
        Properties messages = new Properties();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("messages.properties");
             Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            messages.load(reader);
        }
        List<String> missing = new ArrayList<>();
        for (Path jsp : jsps()) {
            Matcher matcher = KEY.matcher(new String(Files.readAllBytes(jsp), StandardCharsets.UTF_8));
            while (matcher.find()) {
                if (!messages.containsKey(matcher.group(1))) {
                    missing.add(jsp.getFileName() + ": " + matcher.group(1));
                }
            }
        }
        assertThat(missing).isEmpty();
    }

    private static List<Path> jsps() throws IOException {
        try (Stream<Path> files = Files.walk(Paths.get("src/main/webapp/WEB-INF/jsp"))) {
            return files.filter(p -> p.toString().endsWith(".jsp") || p.toString().endsWith(".jspf"))
                    .collect(Collectors.toList());
        }
    }
}
