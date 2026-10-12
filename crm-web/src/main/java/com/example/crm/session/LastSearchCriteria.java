package com.example.crm.session;

import java.io.Serializable;

/** Posledné kritériá vyhľadávania v blackliste – zdieľané so starou appkou (len reťazce). */
public record LastSearchCriteria(String firstName, String lastName, String identifierValue, String state)
        implements Serializable {

    /** Kľúč vo fasáde (v úložisku APP_BLACKLIST_LAST_CRITERIA). */
    public static final String KEY = "BLACKLIST_LAST_CRITERIA";
}
