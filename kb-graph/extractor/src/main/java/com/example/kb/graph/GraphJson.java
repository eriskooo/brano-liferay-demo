package com.example.kb.graph;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * JSON formát grafu – zdroj pravdy v gite (podľa článku: diffovateľný, prežije zmenu verzie DB).
 * Formát je deterministický: zoradené uzly, hrany aj vlastnosti, LF a koncový nový riadok.
 */
public final class GraphJson {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    private static final DefaultPrettyPrinter PRINTER = new DefaultPrettyPrinter()
            .withObjectIndenter(new DefaultIndenter("  ", "\n"))
            .withArrayIndenter(new DefaultIndenter("  ", "\n"));

    private GraphJson() {
    }

    /** Zapíše graf do textu. */
    public static String write(Graph graph) {
        try {
            return MAPPER.writer(PRINTER).writeValueAsString(graph) + "\n";
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Graf sa nedá zapísať do JSON", e);
        }
    }

    /** Prečíta graf z textu. */
    public static Graph read(String json) {
        try {
            return MAPPER.readValue(json, Graph.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Neplatný JSON grafu: " + e.getOriginalMessage(), e);
        }
    }

    /** Prečíta graf zo súboru. */
    public static Graph readFile(Path path) {
        try {
            return read(Files.readString(path, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Zapíše graf do súboru (vytvorí adresáre). */
    public static void writeFile(Graph graph, Path path) {
        try {
            Files.createDirectories(path.toAbsolutePath().getParent());
            Files.writeString(path, write(graph), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
