package com.example.crm.frontend.base.session;

import javax.portlet.PortletRequest;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.portlet.MockPortletSession;
import org.springframework.mock.web.portlet.MockRenderRequest;

/** Request bez Liferay a bez ThreadLocal: portlet request s danou session + servlet request s cookie SSO_ID. */
public class FakeRequestAwareManager implements RequestAwareManager {

    private final MockRenderRequest portletRequest = new MockRenderRequest();
    private final MockHttpServletRequest httpRequest = new MockHttpServletRequest();
    private String ssoSessionId;
    private String currentPageUrl = "/web/crm/blacklist";

    /** Nový request s novou (prázdnou) portlet session. */
    public FakeRequestAwareManager() {
        this(new MockPortletSession());
    }

    /** Request nad danou session ({@code null} = session ešte neexistuje); viac requestov môže zdieľať jednu session. */
    public FakeRequestAwareManager(MockPortletSession session) {
        portletRequest.setSession(session);
    }

    /** Nastaví cookie SSO_ID (ID zdieľanej session). */
    public FakeRequestAwareManager withSsoSessionId(String id) {
        this.ssoSessionId = id;
        httpRequest.setCookies(new Cookie("SSO_ID", id));
        return this;
    }

    /** Nastaví URL aktuálnej stránky portálu. */
    public FakeRequestAwareManager withCurrentPageUrl(String url) {
        this.currentPageUrl = url;
        return this;
    }

    @Override
    public MockRenderRequest getPortletRequest() {
        return portletRequest;
    }

    @Override
    public HttpServletRequest getHttpServletRequest() {
        return httpRequest;
    }

    @Override
    public String getSsoSessionId() {
        return ssoSessionId;
    }

    @Override
    public String getCurrentPageUrl() {
        return currentPageUrl;
    }

    @Override
    public Object getRequestAttribute(String name) {
        return httpRequest.getAttribute(name);
    }

    @Override
    public void setRequestAttribute(String name, Object value) {
        httpRequest.setAttribute(name, value);
    }

    /** Pre testy, ktoré potrebujú typ {@link PortletRequest}. */
    public PortletRequest portletRequest() {
        return portletRequest;
    }
}
