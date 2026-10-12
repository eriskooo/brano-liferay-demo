package com.example.crm.frontend.backend.blacklist;

import java.io.Serializable;

/**
 * Kritériá vyhľadávania. Ako v legacy slúžia zároveň ako formulár (držaný v {@code @SessionAttributes})
 * aj ako dotaz pre backend – preto {@link Serializable}.
 */
public class BlacklistSearchCriteria implements Serializable {

    private static final long serialVersionUID = 1L;

    private String firstName;
    private String lastName;
    private String identifierValue;
    private BlacklistState state;

    /** Aspoň jedno kritérium (medzery sa nerátajú). */
    public boolean isAnyCriterionFilled() {
        return hasText(firstName) || hasText(lastName) || hasText(identifierValue) || state != null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
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

    public String getIdentifierValue() {
        return identifierValue;
    }

    public void setIdentifierValue(String identifierValue) {
        this.identifierValue = identifierValue;
    }

    public BlacklistState getState() {
        return state;
    }

    public void setState(BlacklistState state) {
        this.state = state;
    }
}
