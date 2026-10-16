package com.example.crm.e2e.support;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Pohľad testu do DB backendu. Na reálnom projekte iná implementácia (Oracle, read-only používateľ, iný reset). */
public interface DbProbe {

    /** Vráti DB do stavu seedu (rovnaký skript ako init kontajnera). */
    void reset();

    /** Záznam podľa ID. */
    Optional<RecordRow> record(long id);

    /** Identifikátory záznamu podľa ID. */
    List<IdentifierRow> identifiers(long recordId);

    /** Poznámky záznamu v poradí vloženia. */
    List<NoteRow> notes(long recordId);

    /** Počet záznamov. */
    int recordCount();

    /** Dnešný dátum podľa DB – appky bežia v tom istom pásme (UTC v Dockeri), hostiteľ nemusí. */
    LocalDate today();
}
