package com.example.crm.frontend.blacklist;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.crm.frontend.backend.blacklist.BlacklistState;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuditLogTest {

    private final List<String> lines = new ArrayList<String>();
    private final AuditLog audit = new AuditLog(lines::add);

    @Test
    @DisplayName("Riadky auditu majú pevný formát a poradie kľúčov (kontrakt s novou appkou)")
    void should_writeContractLines_when_eventsRecorded() {
        audit.recordCreated(9, "spravca");
        audit.recordChanged(1, "spravca", BlacklistState.INACTIVE, LocalDate.of(2026, 10, 16));
        audit.noteAdded(1, "spravca");
        audit.identifierRemoved(1, 2, "spravca");

        assertThat(lines).containsExactly(
                "AUDIT event=blacklist.create recordId=9 operator=spravca",
                "AUDIT event=blacklist.change recordId=1 operator=spravca state=INACTIVE validTo=2026-10-16",
                "AUDIT event=blacklist.note recordId=1 operator=spravca",
                "AUDIT event=blacklist.identifier.remove recordId=1 identifierId=2 operator=spravca");
    }

    @Test
    @DisplayName("Chýbajúca hodnota je prázdna, nie „null“")
    void should_writeEmptyValue_when_validToMissing() {
        audit.recordChanged(3, "spravca", BlacklistState.ACTIVE, null);

        assertThat(lines).containsExactly("AUDIT event=blacklist.change recordId=3 operator=spravca state=ACTIVE validTo=");
    }
}
