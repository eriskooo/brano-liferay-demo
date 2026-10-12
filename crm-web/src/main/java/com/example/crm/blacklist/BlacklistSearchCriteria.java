package com.example.crm.blacklist;

import java.io.Serializable;

import jakarta.validation.constraints.AssertTrue;

/**
 * Kritériá vyhľadávania. Rovnako ako v legacy slúžia zároveň ako formulár (držaný v session cez
 * {@code @SessionAttributes}) aj ako dotaz pre backend – preto {@link Serializable}.
 */
public class BlacklistSearchCriteria implements Serializable {

    private String firstName;
    private String lastName;
    private String identifierValue;
    private BlacklistState state;

    /** Náhrada legacy vlastného search validatora: aspoň jedno kritérium (medzery sa nerátajú). */
    @AssertTrue(message = "{blacklist.search.noCriteria}")
    public boolean isAnyCriterionFilled() {
        return hasText(firstName) || hasText(lastName) || hasText(identifierValue) || state != null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getIdentifierValue() { return identifierValue; }
    public void setIdentifierValue(String identifierValue) { this.identifierValue = identifierValue; }
    public BlacklistState getState() { return state; }
    public void setState(BlacklistState state) { this.state = state; }
}
