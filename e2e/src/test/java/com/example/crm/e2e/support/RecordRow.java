package com.example.crm.e2e.support;

import java.time.LocalDate;

/** Riadok tabuľky blacklist_record – len stĺpce, ktoré testy overujú. */
public record RecordRow(long id, String firstName, String lastName, String state, LocalDate validTo) {
}
