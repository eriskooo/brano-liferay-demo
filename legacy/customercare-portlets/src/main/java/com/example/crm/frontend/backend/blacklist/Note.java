package com.example.crm.frontend.backend.blacklist;

import java.time.LocalDateTime;
import java.util.Objects;

/** Poznámka k záznamu. */
public final class Note {

    private final String text;
    private final String author;
    private final LocalDateTime createdAt;

    public Note(String text, String author, LocalDateTime createdAt) {
        this.text = text;
        this.author = author;
        this.createdAt = createdAt;
    }

    public String getText() {
        return text;
    }

    public String getAuthor() {
        return author;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Note)) {
            return false;
        }
        Note that = (Note) other;
        return Objects.equals(text, that.text) && Objects.equals(author, that.author)
                && Objects.equals(createdAt, that.createdAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, author, createdAt);
    }

    @Override
    public String toString() {
        return createdAt + " " + author + ": " + text;
    }
}
