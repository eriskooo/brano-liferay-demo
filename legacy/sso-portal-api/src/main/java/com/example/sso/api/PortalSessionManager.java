package com.example.sso.api;

import javax.servlet.http.HttpServletRequest;

/**
 * API SSO portálu, ako ho vidí CRM. V legacy je to „provided“ závislosť, ktorú dodáva prostredie (JBoss modul);
 * CRM nevie, či je za ňou HTTP, EJB alebo fake – implementáciu vyberá {@link PortalServiceLocator}.
 */
public interface PortalSessionManager {

    /** Cookie, ktorou portál páruje prehliadač so svojou session. */
    String SSO_COOKIE = "SSO_ID";

    /**
     * Vráti session portálu podľa cookie v requeste, alebo {@code null}, ak používateľ nie je prihlásený.
     *
     * @throws SsoUnavailableException ak portál neodpovedá
     */
    PortalSession getPortalSession(HttpServletRequest request);

    /** URL prihlasovacej stránky portálu, ktorá po prihlásení vráti používateľa na {@code returnUrl}. */
    String getLoginUrl(String returnUrl);
}
