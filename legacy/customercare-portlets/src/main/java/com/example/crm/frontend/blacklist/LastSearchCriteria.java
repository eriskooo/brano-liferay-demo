package com.example.crm.frontend.blacklist;

import java.io.Serializable;

/**
 * Posledné kritériá vyhľadávania v zdieľanej session. Len reťazce – prečíta ich Java 8 aj nová appka (Java 21)
 * bez zdieľania doménových tried. Formulár samotný ostáva v {@code @SessionAttributes} (mimo zdieľania).
 */
public class LastSearchCriteria implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Kľúč vo fasáde (v úložisku {@code APP_BLACKLIST_LAST_CRITERIA}). */
    public static final String KEY = "BLACKLIST_LAST_CRITERIA";

    private String firstName;
    private String lastName;
    private String identifierValue;
    private String state;

    public LastSearchCriteria() {
    }

    public LastSearchCriteria(String firstName, String lastName, String identifierValue, String state) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.identifierValue = identifierValue;
        this.state = state;
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

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }
}
