package com.example.crm.frontend.backend.blacklist;

import java.time.LocalDate;

/** Príkaz na vytvorenie záznamu – backend port nezávisí od webového formulára. */
public final class NewBlacklistRecord {

    private final String firstName;
    private final String lastName;
    private final LocalDate birthDate;
    private final StateReason stateReason;
    private final ConditionType conditionType;
    private final LocalDate validFrom;
    private final LocalDate validTo;
    private final IdentifierType identifierType;
    private final String identifierValue;

    public NewBlacklistRecord(String firstName, String lastName, LocalDate birthDate, StateReason stateReason,
                              ConditionType conditionType, LocalDate validFrom, LocalDate validTo,
                              IdentifierType identifierType, String identifierValue) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.birthDate = birthDate;
        this.stateReason = stateReason;
        this.conditionType = conditionType;
        this.validFrom = validFrom;
        this.validTo = validTo;
        this.identifierType = identifierType;
        this.identifierValue = identifierValue;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public LocalDate getBirthDate() {
        return birthDate;
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

    public IdentifierType getIdentifierType() {
        return identifierType;
    }

    public String getIdentifierValue() {
        return identifierValue;
    }
}
