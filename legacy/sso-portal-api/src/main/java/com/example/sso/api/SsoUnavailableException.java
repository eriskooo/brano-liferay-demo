package com.example.sso.api;

/** SSO portál neodpovedá alebo odpovedá chybou – nie je to „neprihlásený používateľ“. */
public class SsoUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SsoUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
