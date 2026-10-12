package com.example.crm.frontend.base.web;

/** Rozhodnutie o prístupe k portletu; HTTP status sa použije v resource fáze (AJAX). */
public enum AccessDecision {
    ALLOWED(200), LOGIN_REQUIRED(401), DENIED(403), SSO_UNAVAILABLE(503);

    private final int httpStatus;

    AccessDecision(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
