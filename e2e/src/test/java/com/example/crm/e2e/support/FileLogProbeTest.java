package com.example.crm.e2e.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileLogProbeTest {

    @TempDir
    Path dir;

    @Test
    @DisplayName("Neexistujúci log súbor = žiadne udalosti ani chyby")
    void should_returnNothing_when_fileMissing() {
        FileLogProbe probe = new FileLogProbe(dir.resolve("missing.log"));
        probe.mark();

        assertThat(probe.auditEvents()).isEmpty();
        assertThat(probe.errors()).isEmpty();
    }

    @Test
    @DisplayName("Číta len riadky zapísané po značke")
    void should_readOnlyLinesAfterMark_when_fileGrows() throws IOException {
        Path file = dir.resolve("app.log");
        append(file, "INFO AUDIT event=blacklist.note recordId=1 operator=starý\n");
        FileLogProbe probe = new FileLogProbe(file);
        probe.mark();

        append(file, "INFO AUDIT event=blacklist.note recordId=2 operator=spravca\n");

        assertThat(probe.auditEvents()).extracting(AuditEvent::event).containsExactly("blacklist.note");
        assertThat(probe.auditEvents().get(0).fields()).containsEntry("recordId", "2");
    }

    @Test
    @DisplayName("Zmenšený súbor (rotácia) sa číta od začiatku")
    void should_readFromStart_when_fileRotated() throws IOException {
        Path file = dir.resolve("app.log");
        append(file, "x".repeat(200) + "\n");
        FileLogProbe probe = new FileLogProbe(file);
        probe.mark();

        Files.writeString(file, "INFO AUDIT event=blacklist.create recordId=9 operator=spravca\n", StandardCharsets.UTF_8);

        assertThat(probe.auditEvents()).extracting(AuditEvent::event).containsExactly("blacklist.create");
    }

    @Test
    @DisplayName("ERROR/FATAL a riadky stacktrace sú chyby, INFO so slovom error nie")
    void should_detectErrorsAndStackFrames_when_present() throws IOException {
        Path file = dir.resolve("app.log");
        FileLogProbe probe = new FileLogProbe(file);
        probe.mark();
        append(file, """
                2026-10-16 10:00:00 INFO  [crm] počet errors: 0
                2026-10-16 10:00:01 ERROR [crm] Zlyhalo spracovanie
                java.lang.IllegalStateException: boom
                \tat com.example.Foo.bar(Foo.java:10)
                2026-10-16 10:00:02 FATAL [crm] koniec
                """);

        assertThat(probe.errors()).hasSize(3);
        assertThat(probe.errors().get(0)).contains("Zlyhalo spracovanie");
    }

    private static void append(Path file, String text) throws IOException {
        Files.writeString(file, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
}
