package com.example.crm.frontend.backend.blacklist;

import java.util.Objects;

/** Identifikátor osoby (Java 8 – trieda namiesto recordu). */
public final class Identifier {

    private final long id;
    private final IdentifierType type;
    private final String value;

    public Identifier(long id, IdentifierType type, String value) {
        this.id = id;
        this.type = type;
        this.value = value;
    }

    public long getId() {
        return id;
    }

    public IdentifierType getType() {
        return type;
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Identifier)) {
            return false;
        }
        Identifier that = (Identifier) other;
        return id == that.id && type == that.type && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, type, value);
    }
}
