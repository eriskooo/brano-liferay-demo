package com.example.crm.session;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Továreň fasády pre request (náhrada legacy singletonu, ktorý si request hľadal cez ThreadLocal). */
@Component
public class CrmSessions {

    private static final Logger LOG = LoggerFactory.getLogger(CrmSessions.class);

    private final IdentityResolver identities;
    private final SharedSessionStore store;
    private final SharedSessionKeys keys;

    public CrmSessions(IdentityResolver identities, SharedSessionStore store, SharedSessionKeys keys) {
        this.identities = identities;
        this.store = store;
        this.keys = keys;
    }

    public CrmSession of(HttpServletRequest request) {
        return new SharedCrmSession(request, identities, store, keys);
    }

    public IdentityResolver identities() {
        return identities;
    }

    /** Flag starej appky pre ladiaci riadok; pri výpadku Redisu prázdno. */
    public Optional<String> legacyReadSource() {
        try {
            return store.legacyReadSource();
        } catch (RuntimeException e) {
            LOG.warn("Flag starej appky sa nedá prečítať: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
