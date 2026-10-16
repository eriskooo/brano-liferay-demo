package com.example.crm.blacklist;

import java.time.LocalDate;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Audit zmien blacklistu – kontrakt zdieľaný so starou appkou: rovnaký riadok v oboch logoch, kontrolujú ho e2e testy.
 * Len ID, operátor a stav – žiadne osobné údaje (mená, hodnoty identifikátorov, text poznámky).
 */
@Component
public class AuditLog {

    private static final Logger LOG = LoggerFactory.getLogger("crm.audit");

    private final Consumer<String> sink;

    public AuditLog() {
        this(LOG::info);
    }

    AuditLog(Consumer<String> sink) {
        this.sink = sink;
    }

    /** Zapíše vytvorenie záznamu. */
    public void recordCreated(long recordId, String operator) {
        write("blacklist.create", "recordId", recordId, "operator", operator);
    }

    /** Zapíše zmenu stavu a platnosti (platnosť po doplnení dneška službou). */
    public void recordChanged(long recordId, String operator, BlacklistState state, LocalDate validTo) {
        write("blacklist.change", "recordId", recordId, "operator", operator, "state", state, "validTo", validTo);
    }

    /** Zapíše pridanie poznámky (bez textu). */
    public void noteAdded(long recordId, String operator) {
        write("blacklist.note", "recordId", recordId, "operator", operator);
    }

    /** Zapíše odstránenie identifikátora (bez hodnoty). */
    public void identifierRemoved(long recordId, long identifierId, String operator) {
        write("blacklist.identifier.remove", "recordId", recordId, "identifierId", identifierId, "operator", operator);
    }

    private void write(String event, Object... keyValues) {
        StringBuilder line = new StringBuilder("AUDIT event=").append(event);
        for (int i = 0; i < keyValues.length; i += 2) {
            Object value = keyValues[i + 1];
            line.append(' ').append(keyValues[i]).append('=').append(value == null ? "" : value);
        }
        sink.accept(line.toString());
    }
}
