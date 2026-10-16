package com.example.crm.e2e.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/** Log v súbore (ako na reálnom serveri; v Dockeri bind mount docker/logs/...). */
public final class FileLogProbe implements LogProbe {

    private static final Pattern ERROR_LEVEL = Pattern.compile("\\b(ERROR|FATAL)\\b");
    private static final Pattern STACK_FRAME = Pattern.compile("^\\s+at [\\w.$]+\\(.*\\)$");

    private final Path file;
    private long offset;

    public FileLogProbe(Path file) {
        this.file = file;
    }

    @Override
    public void mark() {
        offset = size();
    }

    @Override
    public List<AuditEvent> auditEvents() {
        return linesSinceMark().stream().map(AuditEvent::parse).flatMap(Optional::stream).toList();
    }

    @Override
    public List<String> errors() {
        return linesSinceMark().stream()
                .filter(line -> ERROR_LEVEL.matcher(line).find() || STACK_FRAME.matcher(line).matches())
                .toList();
    }

    @Override
    public String excerpt() {
        return String.join("\n", linesSinceMark());
    }

    private List<String> linesSinceMark() {
        if (!Files.exists(file)) {
            return List.of();
        }
        long size = size();
        // Menší súbor než pri značke = rotácia; nový obsah je od začiatku.
        long from = size < offset ? 0 : offset;
        try (SeekableByteChannel channel = Files.newByteChannel(file)) {
            channel.position(from);
            ByteBuffer buffer = ByteBuffer.allocate((int) (size - from));
            while (buffer.hasRemaining() && channel.read(buffer) > 0) {
                // číta, kým nie je buffer plný
            }
            return new String(buffer.array(), 0, buffer.position(), StandardCharsets.UTF_8).lines().toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Log " + file + " sa nedá prečítať", e);
        }
    }

    private long size() {
        try {
            return Files.exists(file) ? Files.size(file) : 0;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
