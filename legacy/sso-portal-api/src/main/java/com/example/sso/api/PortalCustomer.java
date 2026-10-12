package com.example.sso.api;

import java.io.Serializable;

/** Zákazník, s ktorým operátor práve pracuje. V legacy ho vyberá SSO portál, nie CRM. */
public class PortalCustomer implements Serializable {

    private static final long serialVersionUID = 1L;

    private String customerId;
    private String displayName;

    public PortalCustomer() {
    }

    public PortalCustomer(String customerId, String displayName) {
        this.customerId = customerId;
        this.displayName = displayName;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
}
