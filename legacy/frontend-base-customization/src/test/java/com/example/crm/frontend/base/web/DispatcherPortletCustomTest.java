package com.example.crm.frontend.base.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.crm.frontend.base.acl.Privilege;
import java.util.ArrayList;
import java.util.List;
import javax.portlet.MimeResponse;
import javax.portlet.PortletRequest;
import javax.portlet.ResourceResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.portlet.MockActionRequest;
import org.springframework.mock.web.portlet.MockActionResponse;
import org.springframework.mock.web.portlet.MockRenderRequest;
import org.springframework.mock.web.portlet.MockRenderResponse;
import org.springframework.mock.web.portlet.MockResourceRequest;
import org.springframework.mock.web.portlet.MockResourceResponse;

class DispatcherPortletCustomTest {

    private final PortletAccessGuard guard = mock(PortletAccessGuard.class);
    private final TestablePortlet portlet = new TestablePortlet(guard);

    @Test
    @DisplayName("Render bez prihlásenia vykreslí výzvu s login URL")
    void should_includeLoginPrompt_when_renderWithoutLogin() throws Exception {
        portlet.setRequiredPrivilege("BLACKLIST_VIEW");
        when(guard.decide(true, Privilege.BLACKLIST_VIEW)).thenReturn(AccessDecision.LOGIN_REQUIRED);
        when(guard.loginUrl()).thenReturn("/sso/login?returnUrl=%2Fweb%2Fcrm%2Fblacklist");
        MockRenderRequest request = new MockRenderRequest();

        portlet.doRenderService(request, new MockRenderResponse());

        assertThat(portlet.included).containsExactly("/WEB-INF/jsp/common/login-required.jsp");
        assertThat(request.getAttribute("loginUrl")).isEqualTo("/sso/login?returnUrl=%2Fweb%2Fcrm%2Fblacklist");
    }

    @Test
    @DisplayName("Render bez privilégia portletu vykreslí 403 s menom privilégia")
    void should_includeForbidden_when_renderDenied() throws Exception {
        portlet.setRequiredPrivilege("DEACTIVATION");
        when(guard.decide(true, Privilege.DEACTIVATION)).thenReturn(AccessDecision.DENIED);
        MockRenderRequest request = new MockRenderRequest();

        portlet.doRenderService(request, new MockRenderResponse());

        assertThat(portlet.included).containsExactly("/WEB-INF/jsp/error/403.jsp");
        assertThat(request.getAttribute("privilege")).isEqualTo("DEACTIVATION");
    }

    @Test
    @DisplayName("Render pri nedostupnom SSO vykreslí chybovú stránku SSO")
    void should_includeSsoError_when_portalDown() throws Exception {
        when(guard.decide(anyBoolean(), any())).thenReturn(AccessDecision.SSO_UNAVAILABLE);

        portlet.doRenderService(new MockRenderRequest(), new MockRenderResponse());

        assertThat(portlet.included).containsExactly("/WEB-INF/jsp/error/sso.jsp");
    }

    @Test
    @DisplayName("Action bez prihlásenia presmeruje na SSO login")
    void should_redirectToLogin_when_actionWithoutLogin() throws Exception {
        when(guard.decide(anyBoolean(), any())).thenReturn(AccessDecision.LOGIN_REQUIRED);
        when(guard.loginUrl()).thenReturn("/sso/login?returnUrl=%2F");
        MockActionResponse response = new MockActionResponse();

        portlet.doActionService(new MockActionRequest(), response);

        assertThat(response.getRedirectedUrl()).isEqualTo("/sso/login?returnUrl=%2F");
    }

    @Test
    @DisplayName("AJAX (resource) bez prihlásenia dostane HTTP 401 bez HTML")
    void should_return401Status_when_resourceWithoutLogin() throws Exception {
        when(guard.decide(anyBoolean(), any())).thenReturn(AccessDecision.LOGIN_REQUIRED);
        MockResourceResponse response = new MockResourceResponse();

        portlet.doResourceService(new MockResourceRequest(), response);

        assertThat(response.getProperty(ResourceResponse.HTTP_STATUS_CODE)).isEqualTo("401");
        assertThat(response.getContentAsString()).isEmpty();
        assertThat(portlet.included).isEmpty();
    }

    @Test
    @DisplayName("AJAX bez privilégia dostane HTTP 403")
    void should_return403Status_when_resourceDenied() throws Exception {
        portlet.setRequiredPrivilege("BLACKLIST_VIEW");
        when(guard.decide(true, Privilege.BLACKLIST_VIEW)).thenReturn(AccessDecision.DENIED);
        MockResourceResponse response = new MockResourceResponse();

        portlet.doResourceService(new MockResourceRequest(), response);

        assertThat(response.getProperty(ResourceResponse.HTTP_STATUS_CODE)).isEqualTo("403");
    }

    @Test
    @DisplayName("Portlet s loginRequired=false a bez privilégia (hlavička) sa pýta strážcu s false a null")
    void should_passConfigToGuard_when_loginNotRequired() throws Exception {
        portlet.setLoginRequired(false);
        when(guard.decide(false, null)).thenReturn(AccessDecision.DENIED);

        portlet.doResourceService(new MockResourceRequest(), new MockResourceResponse());

        assertThat(portlet.decisions).containsExactly(AccessDecision.DENIED);
    }

    /** Portlet bez Spring kontextu: strážca je podstrčený, include sa len zaznamená. */
    static class TestablePortlet extends DispatcherPortletCustom {

        final List<String> included = new ArrayList<>();
        final List<AccessDecision> decisions = new ArrayList<>();
        private final PortletAccessGuard guard;

        TestablePortlet(PortletAccessGuard guard) {
            this.guard = guard;
        }

        @Override
        protected PortletAccessGuard guard() {
            return new PortletAccessGuard(null, null) {
                @Override
                public AccessDecision decide(boolean loginRequired, Privilege required) {
                    AccessDecision decision = guard.decide(loginRequired, required);
                    decisions.add(decision);
                    return decision;
                }

                @Override
                public String loginUrl() {
                    return guard.loginUrl();
                }
            };
        }

        @Override
        protected void include(String path, PortletRequest request, MimeResponse response) {
            included.add(path);
        }
    }
}
