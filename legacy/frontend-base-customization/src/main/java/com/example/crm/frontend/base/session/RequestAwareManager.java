package com.example.crm.frontend.base.session;

import javax.portlet.PortletRequest;
import javax.servlet.http.HttpServletRequest;

/**
 * Prístup k aktuálnemu requestu pre singletony (fasáda session, ACL). Legacy vzor: singleton si request
 * vždy dohľadá z vlákna, preto funguje len vo vlákne requestu (nie v {@code @Async} ani vo vlastnom executore).
 */
public interface RequestAwareManager {

    /** Portlet request aktuálneho vlákna. */
    PortletRequest getPortletRequest();

    /** Pôvodný servlet request prehliadača (cookies, atribúty zdieľané všetkými portletmi stránky). */
    HttpServletRequest getHttpServletRequest();

    /** ID session SSO portálu z cookie, alebo {@code null}. */
    String getSsoSessionId();

    /** URL stránky portálu, na ktorej je používateľ (návrat po prihlásení). */
    String getCurrentPageUrl();

    /** Atribút pôvodného servlet requestu. */
    Object getRequestAttribute(String name);

    /** Nastaví atribút pôvodného servlet requestu (cache na jeden HTTP request). */
    void setRequestAttribute(String name, Object value);
}
