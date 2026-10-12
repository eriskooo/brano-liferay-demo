package com.example.crm.frontend.base.web;

import com.example.crm.frontend.base.acl.OperatorAcl;
import com.example.crm.frontend.base.acl.Privilege;
import com.example.crm.frontend.base.session.SessionManagerCustom;
import com.example.sso.api.SsoUnavailableException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Logika kontroly v {@code DispatcherPortletCustom} oddelená od portletu, aby sa dala testovať bez portálu.
 * V legacy je táto kontrola zahrabaná v DispatcherPortlet podtriede; pri migrácii z nej bude interceptor.
 */
public class PortletAccessGuard {

    private static final Log LOG = LogFactory.getLog(PortletAccessGuard.class);

    private final SessionManagerCustom sessionManager;
    private final OperatorAcl acl;

    public PortletAccessGuard(SessionManagerCustom sessionManager, OperatorAcl acl) {
        this.sessionManager = sessionManager;
        this.acl = acl;
    }

    /** Rozhodne o prístupe; {@code required == null} znamená portlet bez privilégia. */
    public AccessDecision decide(boolean loginRequired, Privilege required) {
        try {
            boolean loggedIn = sessionManager.isLoggedIn();
            if (loginRequired && !loggedIn) {
                return AccessDecision.LOGIN_REQUIRED;
            }
            if (required != null && !acl.hasPrivilege(required)) {
                return AccessDecision.DENIED;
            }
            return AccessDecision.ALLOWED;
        } catch (SsoUnavailableException e) {
            LOG.error("SSO portál je nedostupný", e);
            return AccessDecision.SSO_UNAVAILABLE;
        }
    }

    /** URL prihlásenia s návratom na aktuálnu stránku. */
    public String loginUrl() {
        return sessionManager.getLoginUrl();
    }
}
