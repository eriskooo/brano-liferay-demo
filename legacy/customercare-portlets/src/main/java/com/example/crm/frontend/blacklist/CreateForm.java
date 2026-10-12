package com.example.crm.frontend.blacklist;

import com.example.crm.frontend.backend.blacklist.ConditionType;
import com.example.crm.frontend.backend.blacklist.IdentifierType;
import com.example.crm.frontend.backend.blacklist.NewBlacklistRecord;
import com.example.crm.frontend.backend.blacklist.StateReason;
import java.io.Serializable;
import java.time.LocalDate;

/** Formulár nového záznamu, držaný v {@code @SessionAttributes} počas AJAX refreshov aj chýb validácie. */
public class CreateForm implements Serializable {

    private static final long serialVersionUID = 1L;

    private String firstName;
    private String lastName;
    private LocalDate birthDate;
    private StateReason stateReason;
    private ConditionType conditionType;
    private LocalDate validFrom;
    private LocalDate validTo;
    private IdentifierType identifierType;
    private String identifierValue;

    /** Príkaz pre backend (hodnoty sú už zvalidované). */
    public NewBlacklistRecord toNewRecord() {
        return new NewBlacklistRecord(firstName.trim(), lastName.trim(), birthDate, stateReason, conditionType,
                validFrom, validTo, identifierType, identifierValue.trim());
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }

    public StateReason getStateReason() {
        return stateReason;
    }

    public void setStateReason(StateReason stateReason) {
        this.stateReason = stateReason;
    }

    public ConditionType getConditionType() {
        return conditionType;
    }

    public void setConditionType(ConditionType conditionType) {
        this.conditionType = conditionType;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(LocalDate validFrom) {
        this.validFrom = validFrom;
    }

    public LocalDate getValidTo() {
        return validTo;
    }

    public void setValidTo(LocalDate validTo) {
        this.validTo = validTo;
    }

    public IdentifierType getIdentifierType() {
        return identifierType;
    }

    public void setIdentifierType(IdentifierType identifierType) {
        this.identifierType = identifierType;
    }

    public String getIdentifierValue() {
        return identifierValue;
    }

    public void setIdentifierValue(String identifierValue) {
        this.identifierValue = identifierValue;
    }
}
