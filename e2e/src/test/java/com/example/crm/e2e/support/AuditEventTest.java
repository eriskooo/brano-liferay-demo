package com.example.crm.e2e.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuditEventTest {

    @Test
    @DisplayName("Riadok logu s AUDIT sa rozparsuje na event a polia bez ohľadu na prefix logu")
    void should_parseEventAndFields_when_lineContainsAudit() {
        String line = "2026-10-16 10:00:00,123 INFO  [http-exec-1] [crm.audit] "
                + "AUDIT event=blacklist.change recordId=1 operator=spravca state=INACTIVE validTo=2026-10-16";

        assertThat(AuditEvent.parse(line)).contains(new AuditEvent("blacklist.change",
                Map.of("recordId", "1", "operator", "spravca", "state", "INACTIVE", "validTo", "2026-10-16")));
    }

    @Test
    @DisplayName("Prázdna hodnota ostane prázdna")
    void should_keepEmptyValue_when_valueMissing() {
        assertThat(AuditEvent.parse("AUDIT event=blacklist.change recordId=3 validTo="))
                .hasValueSatisfying(e -> assertThat(e.fields()).containsEntry("validTo", ""));
    }

    @Test
    @DisplayName("Riadok bez AUDIT nie je audit")
    void should_returnEmpty_when_lineIsNotAudit() {
        assertThat(AuditEvent.parse("INFO  [crm] niečo iné")).isEmpty();
        assertThat(AuditEvent.parse("")).isEmpty();
    }
}
