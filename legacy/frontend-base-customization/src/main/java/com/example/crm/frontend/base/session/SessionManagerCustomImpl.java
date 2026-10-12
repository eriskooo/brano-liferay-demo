package com.example.crm.frontend.base.session;

import com.example.sso.api.PortalCustomer;
import com.example.sso.api.PortalOperator;
import com.example.sso.api.PortalSession;
import com.example.sso.api.PortalSessionManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Singleton fasády. Robí dve rôzne veci ako legacy: identitu a zákazníka číta zo SSO portálu,
 * všetko ostatné zo {@link SessionStore} (duálna session). Request si berie z {@link RequestAwareManager}.
 */
public class SessionManagerCustomImpl implements SessionManagerCustom {

    public static final String PREFIX = PortletSessionStore.PREFIX;

    static final String PORTAL_SESSION_CACHE = SessionManagerCustomImpl.class.getName() + ".PORTAL_SESSION";

    // Značka „portál povedal, že session nie je“ – aj prázdny výsledok sa v rámci requestu cachuje.
    private static final Object NO_SESSION = new Object();

    private final RequestAwareManager requests;
    private final PortalSessionManager portal;
    private final SessionStore store;

    public SessionManagerCustomImpl(RequestAwareManager requests, PortalSessionManager portal, SessionStore store) {
        this.requests = requests;
        this.portal = portal;
        this.store = store;
    }

    @Override
    public PortalOperator getOperator() {
        PortalSession session = portalSession();
        return session == null ? null : session.getOperator();
    }

    @Override
    public PortalCustomer getCustomer() {
        PortalSession session = portalSession();
        return session == null ? null : session.getCustomer();
    }

    @Override
    public boolean isLoggedIn() {
        return getOperator() != null;
    }

    @Override
    public String getLoginUrl() {
        return portal.getLoginUrl(requests.getCurrentPageUrl());
    }

    @Override
    public void pushInfo(String key) {
        push(new UiMessage(UiMessage.Level.INFO, key));
    }

    @Override
    public void pushError(String key) {
        push(new UiMessage(UiMessage.Level.ERROR, key));
    }

    @Override
    public List<UiMessage> popMessages() {
        List<UiMessage> messages = messages();
        if (messages.isEmpty()) {
            return Collections.emptyList();
        }
        store.remove(SharedKeys.MESSAGES);
        return messages;
    }

    @Override
    public void setGlobalAttribute(String key, Object value) {
        store.put(PREFIX + key, value);
    }

    @Override
    public Object getGlobalAttribute(String key) {
        return store.get(PREFIX + key);
    }

    @Override
    public void removeGlobalAttribute(String key) {
        store.remove(PREFIX + key);
    }

    @Override
    public void flushAll() {
        store.clear();
    }

    private PortalSession portalSession() {
        // Cache na pôvodnom servlet requeste: hlavička aj obsahový portlet stránky zavolajú SSO spolu raz.
        Object cached = requests.getRequestAttribute(PORTAL_SESSION_CACHE);
        if (cached == NO_SESSION) {
            return null;
        }
        if (cached instanceof PortalSession) {
            return (PortalSession) cached;
        }
        PortalSession session = portal.getPortalSession(requests.getHttpServletRequest());
        requests.setRequestAttribute(PORTAL_SESSION_CACHE, session == null ? NO_SESSION : session);
        return session;
    }

    private void push(UiMessage message) {
        List<UiMessage> messages = new ArrayList<>(messages());
        messages.add(message);
        store.put(SharedKeys.MESSAGES, messages);
    }

    @SuppressWarnings("unchecked")
    private List<UiMessage> messages() {
        Object value = store.get(SharedKeys.MESSAGES);
        return value == null ? Collections.<UiMessage>emptyList() : (List<UiMessage>) value;
    }
}
