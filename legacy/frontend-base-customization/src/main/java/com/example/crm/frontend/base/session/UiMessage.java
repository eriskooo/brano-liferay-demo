package com.example.crm.frontend.base.session;

import java.io.Serializable;
import java.util.Objects;

/**
 * Hláška pre používateľa (i18n kľúč). Prežije action → render a v podprojekte 2 aj prechod do novej appky,
 * preto je to jednoduché DTO serializovateľné do JSON (žiadna Java serializácia doménových tried).
 */
public class UiMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Úroveň hlášky. */
    public enum Level { INFO, ERROR }

    private Level level;
    private String key;

    public UiMessage() {
    }

    public UiMessage(Level level, String key) {
        this.level = level;
        this.key = key;
    }

    public Level getLevel() {
        return level;
    }

    public void setLevel(Level level) {
        this.level = level;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof UiMessage)) {
            return false;
        }
        UiMessage that = (UiMessage) other;
        return level == that.level && Objects.equals(key, that.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(level, key);
    }

    @Override
    public String toString() {
        return level + ":" + key;
    }
}
