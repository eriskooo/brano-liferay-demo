package com.example.sso.api;

import java.io.Serializable;

/** Session SSO portálu – zdieľaná s CRM. Identita a zákazník žijú tu, nie v Liferay ani v CRM. */
public class PortalSession implements Serializable {

    private static final long serialVersionUID = 1L;

    private String sessionId;
    private PortalOperator operator;
    private PortalCustomer customer;

    public PortalSession() {
    }

    public PortalSession(String sessionId, PortalOperator operator, PortalCustomer customer) {
        this.sessionId = sessionId;
        this.operator = operator;
        this.customer = customer;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public PortalOperator getOperator() {
        return operator;
    }

    public void setOperator(PortalOperator operator) {
        this.operator = operator;
    }

    public PortalCustomer getCustomer() {
        return customer;
    }

    public void setCustomer(PortalCustomer customer) {
        this.customer = customer;
    }
}
