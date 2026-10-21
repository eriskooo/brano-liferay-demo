package com.example.kb.extract;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RepoFilesTest {

    private static final Path ROOT = Path.of("/repo");

    @Test
    @DisplayName("Do input, target ani src/test sa nevstupuje vôbec")
    void should_notDescend_whenDirIsSkipped() {
        assertThat(RepoFiles.descend(ROOT, ROOT.resolve("input"))).isFalse();
        assertThat(RepoFiles.descend(ROOT, ROOT.resolve("crm-web/target"))).isFalse();
        assertThat(RepoFiles.descend(ROOT, ROOT.resolve("kb-graph/extractor/src/test"))).isFalse();
    }

    @Test
    @DisplayName("Do bežných adresárov a koreňa sa vstupuje")
    void should_descend_whenDirIsRegular() {
        assertThat(RepoFiles.descend(ROOT, ROOT)).isTrue();
        assertThat(RepoFiles.descend(ROOT, ROOT.resolve("crm-web/src/main"))).isTrue();
        assertThat(RepoFiles.descend(ROOT, ROOT.resolve("legacy/src"))).isTrue();
    }
}
