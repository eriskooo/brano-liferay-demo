package com.example.crm.frontend.base.session;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Duálna session: zápis do starej session aj do Redisu, čítanie podľa flagu. Zlyhanie Redisu nikdy nezhodí
 * request – zaloguje sa a pokračuje sa so starou session. Controllery o ničom z toho nevedia (volajú fasádu).
 */
public class DualSessionStore implements SessionStore {

    private static final Log LOG = LogFactory.getLog(DualSessionStore.class);

    private final SessionStore primary;
    private final SessionStore shared;
    private final ReadSourceFlag flag;

    public DualSessionStore(SessionStore primary, SessionStore shared, ReadSourceFlag flag) {
        this.primary = primary;
        this.shared = shared;
        this.flag = flag;
    }

    @Override
    public Object get(String key) {
        if (flag.current() == ReadSource.REDIS) {
            try {
                return shared.get(key);
            } catch (RuntimeException e) {
                LOG.error("Čítanie kľúča " + key + " zo zdieľanej session zlyhalo, fallback na PortletSession", e);
            }
        }
        return primary.get(key);
    }

    @Override
    public void put(String key, Object value) {
        primary.put(key, value);
        try {
            shared.put(key, value);
        } catch (RuntimeException e) {
            LOG.error("Zápis kľúča " + key + " do zdieľanej session zlyhal", e);
        }
    }

    @Override
    public void remove(String key) {
        primary.remove(key);
        try {
            shared.remove(key);
        } catch (RuntimeException e) {
            LOG.error("Odstránenie kľúča " + key + " zo zdieľanej session zlyhalo", e);
        }
    }

    @Override
    public void clear() {
        primary.clear();
        try {
            shared.clear();
        } catch (RuntimeException e) {
            LOG.error("Vymazanie zdieľanej session zlyhalo", e);
        }
    }
}
