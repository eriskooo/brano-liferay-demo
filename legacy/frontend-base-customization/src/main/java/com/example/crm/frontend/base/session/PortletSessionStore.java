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
}
