package com.example.crm.frontend.base.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.liferay.portal.kernel.util.WebKeys;
import com.liferay.portal.model.Group;
import com.liferay.portal.model.Layout;
import com.liferay.portal.theme.ThemeDisplay;
import com.liferay.portal.util.Portal;
import com.liferay.portal.util.PortalUtil;
import javax.portlet.PortletRequest;
import javax.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.portlet.MockRenderRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.portlet.context.PortletRequestAttributes;

class PortletRequestAwareManagerTest {

    private final MockRenderRequest portletRequest = new MockRenderRequest();
    private final MockHttpServletRequest httpRequest = new MockHttpServletRequest();
    private final PortletRequestAwareManager manager = new PortletRequestAwareManager();

    @BeforeEach
    void bindRequestToThread() throws Exception {
        // To isté robí FrameworkPortlet pred každou fázou – fasáda (singleton) si request berie z vlákna.
        RequestContextHolder.setRequestAttributes(new PortletRequestAttributes(portletRequest));
        Portal portal = mock(Portal.class);
        when(portal.getHttpServletRequest(any(PortletRequest.class))).thenReturn(httpRequest);
        when(portal.getOriginalServletRequest(httpRequest)).thenReturn(httpRequest);
        new PortalUtil().setPortal(portal);
    }

    @AfterEach
    void cleanUp() {
        RequestContextHolder.resetRequestAttributes();
        new PortalUtil().setPortal(null);
    }

    @Test
    @DisplayName("Vráti portlet request aktuálneho vlákna a pôvodný servlet request cez PortalUtil")
    void should_returnRequestsOfCurrentThread_when_insidePortletRequest() {
        assertThat(manager.getPortletRequest()).isSameAs(portletRequest);
        assertThat(manager.getHttpServletRequest()).isSameAs(httpRequest);
    }

    @Test
    @DisplayName("Mimo vlákna requestu zlyhá so zrozumiteľnou správou")
    void should_failWithClearMessage_when_outsideRequestThread() {
        RequestContextHolder.resetRequestAttributes();

        assertThatThrownBy(manager::getPortletRequest)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RequestContextHolder");
    }

    @Test
    @DisplayName("ID SSO session číta z cookie pôvodného requestu")
    void should_readSsoSessionId_when_cookiePresent() {
        httpRequest.setCookies(new Cookie("SSO_ID", "abc-123"));

        assertThat(manager.getSsoSessionId()).isEqualTo("abc-123");
    }

    @Test
    @DisplayName("URL aktuálnej stránky zloží z ThemeDisplay (/web + site + stránka)")
    void should_composePageUrl_when_themeDisplayPresent() throws Exception {
        Group group = mock(Group.class);
        when(group.getFriendlyURL()).thenReturn("/crm");
        Layout layout = mock(Layout.class);
        when(layout.getGroup()).thenReturn(group);
        when(layout.getFriendlyURL()).thenReturn("/blacklist");
        ThemeDisplay themeDisplay = mock(ThemeDisplay.class);
        when(themeDisplay.getLayout()).thenReturn(layout);
        when(themeDisplay.getPathFriendlyURLPublic()).thenReturn("/web");
        portletRequest.setAttribute(WebKeys.THEME_DISPLAY, themeDisplay);

        assertThat(manager.getCurrentPageUrl()).isEqualTo("/web/crm/blacklist");
    }

    @Test
    @DisplayName("Bez ThemeDisplay vráti koreň portálu")
    void should_returnRoot_when_themeDisplayMissing() {
        assertThat(manager.getCurrentPageUrl()).isEqualTo("/");
    }

    @Test
    @DisplayName("Atribúty requestu ukladá do pôvodného servlet requestu (zdieľa ich celá stránka)")
    void should_storeAttributesInOriginalServletRequest_when_set() {
        manager.setRequestAttribute("x", "y");

        assertThat(httpRequest.getAttribute("x")).isEqualTo("y");
        assertThat(manager.getRequestAttribute("x")).isEqualTo("y");
    }
}
