package com.example.crm.frontend.base.session;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import javax.portlet.PortletSession;

/**
 * Stará session: {@link PortletSession} v {@code APPLICATION_SCOPE} – vidia ju všetky portlety tohto WAR-u.
 * Prefix {@code APP_} oddeľuje kľúče fasády od ostatných atribútov (po migrácii na jednu HttpSession
 * nahrádza namespace, ktorý dnes robí portlet scope).
 */
public class PortletSessionStore implements SessionStore {

    public static final String PREFIX = "APP_";

    private final RequestAwareManager requests;

    public PortletSessionStore(RequestAwareManager requests) {
        this.requests = requests;
    }

    @Override
    public Object get(String key) {
        PortletSession session = requests.getPortletRequest().getPortletSession(false);
        return session == null ? null : session.getAttribute(key, PortletSession.APPLICATION_SCOPE);
    }

    @Override
    public void put(String key, Object value) {
        requests.getPortletRequest().getPortletSession(true).setAttribute(key, value, PortletSession.APPLICATION_SCOPE);
    }

    @Override
    public void remove(String key) {
        PortletSession session = requests.getPortletRequest().getPortletSession(false);
        if (session != null) {
            session.removeAttribute(key, PortletSession.APPLICATION_SCOPE);
        }
    }

    @Override
    public void clear() {
        PortletSession session = requests.getPortletRequest().getPortletSession(false);
        if (session == null) {
            return;
        }
        List<String> keys = new ArrayList<>();
        Enumeration<String> names = session.getAttributeNames(PortletSession.APPLICATION_SCOPE);
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            if (name.startsWith(PREFIX)) {
                keys.add(name);
            }
        }
        for (String key : keys) {
            session.removeAttribute(key, PortletSession.APPLICATION_SCOPE);
        }
    }

    /** SSO_ID operátora, ktorému patrí obsah tejto PortletSession. */
    public static final String OWNER = PREFIX + "SSO_OWNER";
    private static final String PORTLET_SCOPE_PREFIX = "javax.portlet.p.";

    /**
     * Liferay session prežije odhlásenie/prihlásenie iného operátora v SSO (JSESSIONID sa nemení). Bez tejto kontroly
     * by operátor B videl stav A (hlavička, formuláre v @SessionAttributes) – pri flagu PORTLET, nie pri REDIS.
     */
    public void ensureOwner() {
        String current = requests.getSsoSessionId();
        if (current == null) {
            return;
        }
        // Session vytvoríme hneď, aby mala vlastníka od prvého requestu – inak by ju „bez vlastníka“ prevzal ďalší operátor.
        PortletSession session = requests.getPortletRequest().getPortletSession(true);
        Object owner = session.getAttribute(OWNER, PortletSession.APPLICATION_SCOPE);
        if (owner != null && !owner.equals(current)) {
            List<String> stale = new ArrayList<>();
            Enumeration<String> names = session.getAttributeNames(PortletSession.APPLICATION_SCOPE);
            while (names.hasMoreElements()) {
                String name = names.nextElement();
                if (name.startsWith(PREFIX) || name.startsWith(PORTLET_SCOPE_PREFIX)) {
                    stale.add(name);
                }
            }
            for (String name : stale) {
                session.removeAttribute(name, PortletSession.APPLICATION_SCOPE);
            }
        }
        session.setAttribute(OWNER, current, PortletSession.APPLICATION_SCOPE);
    }
}
