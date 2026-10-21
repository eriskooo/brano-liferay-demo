package com.example.kb.extract;

import java.net.URISyntaxException;
import java.nio.file.Path;

/** Cesty k testovacím vstupom v src/test/resources/fixtures. */
final class Fixtures {

    private Fixtures() {
    }

    static Path dir(String name) {
        try {
            return Path.of(Fixtures.class.getResource("/fixtures/" + name).toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Koreň repa – testy bežia z kb-graph/extractor. */
    static Path repoRoot() {
        return Path.of("../..").toAbsolutePath().normalize();
    }
}
