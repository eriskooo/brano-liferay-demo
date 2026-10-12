package com.example.crm.frontend.base.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.crm.frontend.base.acl.OperatorAcl;
import com.example.crm.frontend.base.acl.Privilege;
import com.example.crm.frontend.base.session.SessionManagerCustom;
import com.example.sso.api.SsoUnavailableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PortletAccessGuardTest {

    private final SessionManagerCustom sessionManager = mock(SessionManagerCustom.class);
    private final OperatorAcl acl = mock(OperatorAcl.class);
    private final PortletAccessGuard guard = new PortletAccessGuard(sessionManager, acl);

    @Test
    @DisplayName("Neprihlásený pri portlete s povinným prihlásením → LOGIN_REQUIRED")
    void should_requireLogin_when_notLoggedIn() {
        assertThat(guard.decide(true, Privilege.BLACKLIST_VIEW)).isEqualTo(AccessDecision.LOGIN_REQUIRED);
    }

    @Test
    @DisplayName("Portlet bez povinného prihlásenia (hlavička) pustí aj neprihláseného")
    void should_allow_when_loginNotRequiredAndNoPrivilege() {
        assertThat(guard.decide(false, null)).isEqualTo(AccessDecision.ALLOWED);
    }

    @Test
    @DisplayName("Prihlásený bez privilégia portletu → DENIED")
    void should_deny_when_privilegeMissing() {
        when(sessionManager.isLoggedIn()).thenReturn(true);

        assertThat(guard.decide(true, Privilege.DEACTIVATION)).isEqualTo(AccessDecision.DENIED);
    }

    @Test
    @DisplayName("Prihlásený s privilégiom alebo portlet bez privilégia → ALLOWED")
    void should_allow_when_privilegePresentOrNotRequired() {
        when(sessionManager.isLoggedIn()).thenReturn(true);
        when(acl.hasPrivilege(Privilege.BLACKLIST_VIEW)).thenReturn(true);

        assertThat(guard.decide(true, Privilege.BLACKLIST_VIEW)).isEqualTo(AccessDecision.ALLOWED);
        assertThat(guard.decide(true, null)).isEqualTo(AccessDecision.ALLOWED);
    }

    @Test
    @DisplayName("Nedostupný SSO portál → SSO_UNAVAILABLE (HTTP 503), nie výnimka")
    void should_reportSsoUnavailable_when_portalDown() {
        when(sessionManager.isLoggedIn()).thenThrow(new SsoUnavailableException("down", null));

        AccessDecision decision = guard.decide(true, Privilege.BLACKLIST_VIEW);

        assertThat(decision).isEqualTo(AccessDecision.SSO_UNAVAILABLE);
        assertThat(decision.getHttpStatus()).isEqualTo(503);
        assertThat(AccessDecision.LOGIN_REQUIRED.getHttpStatus()).isEqualTo(401);
        assertThat(AccessDecision.DENIED.getHttpStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("Login URL berie z fasády")
    void should_delegateLoginUrl_when_asked() {
        when(sessionManager.getLoginUrl()).thenReturn("/sso/login?returnUrl=%2F");

        assertThat(guard.loginUrl()).isEqualTo("/sso/login?returnUrl=%2F");
    }
}
