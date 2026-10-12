package com.example.sso.api;

import java.io.Serializable;

/** Operátor prihlásený v SSO portáli. POJO s bezparametrickým konštruktorom kvôli JSON (Java 8, bez recordov). */
public class PortalOperator implements Serializable {

    private static final long serialVersionUID = 1L;

    private String login;
    private String displayName;

    public PortalOperator() {
    }

    public PortalOperator(String login, String displayName) {
        this.login = login;
        this.displayName = displayName;
    }

    public String getLogin() {
        return login;
    }

    public void setLogin(String login) {
        this.login = login;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
}
