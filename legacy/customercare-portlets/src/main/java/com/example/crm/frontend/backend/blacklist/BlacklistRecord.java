package com.example.crm.frontend.backend.blacklist;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Záznam blacklistu ne-zákazníka (osoba, ktorá nie je zákazníkom, ale má obmedzené objednávky).
 * Meniteľné časti mení len služba – gettery používajú JSP cez EL.
 */
public class BlacklistRecord {

    private final long id;
    private final String firstName;
    private final String lastName;
    private final LocalDate birthDate;
    private final StateReason stateReason;
    private final ConditionType conditionType;
    private final LocalDate validFrom;
    private final List<Identifier> identifiers = new ArrayList<>();
    private final List<Note> notes = new ArrayList<>();
    private BlacklistState state;
    private LocalDate validTo;

    BlacklistRecord(long id, String firstName, String lastName, LocalDate birthDate, BlacklistState state,
                    StateReason stateReason, ConditionType conditionType, LocalDate validFrom, LocalDate validTo) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.birthDate = birthDate;
        this.state = state;
        this.stateReason = stateReason;
        this.conditionType = conditionType;
        this.validFrom = validFrom;
        this.validTo = validTo;
    }

    public long getId() {
        return id;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getFullName() {
        return firstName + " " + lastName;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public BlacklistState getState() {
        return state;
    }

    public StateReason getStateReason() {
        return stateReason;
    }

    public ConditionType getConditionType() {
        return conditionType;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public LocalDate getValidTo() {
        return validTo;
    }

    public List<Identifier> getIdentifiers() {
        return Collections.unmodifiableList(identifiers);
    }

    public List<Note> getNotes() {
        return Collections.unmodifiableList(notes);
    }

    void changeState(BlacklistState newState, LocalDate newValidTo) {
        this.state = newState;
        this.validTo = newValidTo;
    }

    void addIdentifier(Identifier identifier) {
        identifiers.add(identifier);
    }

    void removeIdentifier(Identifier identifier) {
        identifiers.remove(identifier);
    }

    void addNote(Note note) {
        notes.add(note);
    }
}
