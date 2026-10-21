package com.example.kb.extract;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/** Prechádzanie repa: preskočí klientske podklady, build výstupy a lokálne (git-ignorované) súbory. */
public final class RepoFiles {

    private static final Set<String> SKIPPED_DIRS =
            Set.of("input", "target", "node_modules", ".git", ".superpowers", ".idea");
    private static final Set<String> SKIPPED_FILES = Set.of("dependency-reduced-pom.xml");

    private RepoFiles() {
    }

    /** Nájde súbory pod koreňom (rekurzívne), ktoré spĺňajú podmienku; zoradené podľa relatívnej cesty. */
    public static List<Path> find(Path root, Predicate<Path> match) {
        List<Path> found = new ArrayList<>();
        try {
            // Do preskočených adresárov sa nevstupuje vôbec – input/ má gigabajty a nesmie sa čítať ani listovať.
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    return descend(root, dir) ? FileVisitResult.CONTINUE : FileVisitResult.SKIP_SUBTREE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (attrs.isRegularFile() && !skippedFile(file) && match.test(file)) {
                        found.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        found.sort(Comparator.comparing((Path p) -> rel(root, p)));
        return List.copyOf(found);
    }

    /** Relatívna cesta s lomkami – rovnaká na Windows aj Linuxe, aby structure.json nezávisel od OS. */
    public static String rel(Path root, Path file) {
        return root.toAbsolutePath().normalize().relativize(file.toAbsolutePath().normalize())
                .toString().replace('\\', '/');
    }

    /**
     * Či vstúpiť do adresára. Kontrolujú sa len segmenty pod koreňom – testové fixtures samy ležia
     * v target/test-classes; src/test nie je súčasť systému (fixtures by sa tvárili ako skutočné moduly).
     */
    static boolean descend(Path root, Path dir) {
        Path relative = root.toAbsolutePath().normalize().relativize(dir.toAbsolutePath().normalize());
        String previous = "";
        for (Path segment : relative) {
            String name = segment.toString();
            if (SKIPPED_DIRS.contains(name) || (previous.equals("src") && name.equals("test"))) {
                return false;
            }
            previous = name;
        }
        return true;
    }

    private static boolean skippedFile(Path file) {
        String name = file.getFileName().toString();
        return SKIPPED_FILES.contains(name) || name.startsWith("LOCAL-");
    }
}
