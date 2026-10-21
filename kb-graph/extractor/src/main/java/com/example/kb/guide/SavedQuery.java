package com.example.kb.guide;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Uložený dotaz z kb-graph/queries/*.cypher. Hlavička v komentároch (title, teaches, sql, text) je jediný
 * zdroj textu slajdu – guide sa z nej generuje, takže sa so súbormi nerozíde.
 */
public record SavedQuery(String title, String teaches, String text, String sql, String cypher) {

    private static final String COMMENT = "// ";

    /** Prečíta jeden súbor. */
    public static SavedQuery parse(Path file) {
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        String title = file.getFileName().toString();
        String teaches = "";
        List<String> text = new ArrayList<>();
        List<String> sql = new ArrayList<>();
        List<String> cypher = new ArrayList<>();
        for (String line : lines) {
            String trimmed = line.strip();
            if (trimmed.startsWith(COMMENT + "title:")) {
                title = value(trimmed, "title:");
            } else if (trimmed.startsWith("// teaches:")) {
                teaches = value(trimmed, "teaches:");
            } else if (trimmed.startsWith("// sql:")) {
                // Odsadenie SQL sa zachová (okrem jednej medzery za dvojbodkou) – inak by bol CTE nečitateľný.
                String rest = trimmed.substring("// sql:".length());
                sql.add(rest.startsWith(" ") ? rest.substring(1) : rest);
            } else if (trimmed.startsWith("//")) {
                text.add(trimmed.substring(2).strip());
            } else if (!trimmed.isEmpty()) {
                cypher.add(line.stripTrailing());
            }
        }
        return new SavedQuery(title, teaches, String.join(" ", text).strip(), String.join("\n", sql),
                String.join("\n", cypher));
    }

    /** Prečíta všetky *.cypher v adresári zoradené podľa názvu (číselná predpona = poradie v guide). */
    public static List<SavedQuery> parseAll(Path dir) {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(p -> p.getFileName().toString().endsWith(".cypher"))
                    .sorted()
                    .map(SavedQuery::parse)
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String value(String line, String key) {
        return line.substring(line.indexOf(key) + key.length()).strip();
    }

    @Override
    public String toString() {
        return title;
    }
}
