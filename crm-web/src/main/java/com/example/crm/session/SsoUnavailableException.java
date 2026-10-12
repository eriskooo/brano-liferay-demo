package com.example.crm.session;

/** SSO portál neodpovedá alebo odpovedá chybou – nie je to „neprihlásený“. */
public class SsoUnavailableException extends RuntimeException {

    public SsoUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
