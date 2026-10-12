package com.example.crm.session;

import java.io.Serializable;

/** Operátor prihlásený v SSO portáli (legacy: PortalOperator zo sso-portal-api). */
public record OperatorUser(String login, String displayName) implements Serializable {
}
