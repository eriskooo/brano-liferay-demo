package com.example.crm.e2e.support;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Riadok auditu {@code AUDIT event=… k=v …} – kontrakt zdieľaný starou aj novou appkou. */
public record AuditEvent(String event, Map<String, String> fields) {

    private static final String MARKER = "AUDIT event=";

    /** Rozparsuje riadok logu; prefix (čas, level, logger) sa ignoruje. */
    public static Optional<AuditEvent> parse(String line) {
        int start = line.indexOf(MARKER);
        if (start < 0) {
            return Optional.empty();
        }
        Map<String, String> fields = new LinkedHashMap<>();
        for (String token : line.substring(start + "AUDIT ".length()).trim().split(" ")) {
            int eq = token.indexOf('=');
            if (eq > 0) {
                fields.put(token.substring(0, eq), token.substring(eq + 1));
            }
        }
        String event = fields.remove("event");
        return Optional.of(new AuditEvent(event, Collections.unmodifiableMap(fields)));
    }
}
