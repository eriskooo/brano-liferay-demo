package com.example.crm.session;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;

/** Kto je prihlásený – v produkcii SSO portál, v testoch náhrada (testsupport.TestInfrastructure). */
public interface IdentityResolver {

    /** Identita pre request (výsledok sa cachuje v {@link SsoIdentity#REQUEST_ATTRIBUTE}). */
    Optional<SsoIdentity> resolve(HttpServletRequest request);

    /** URL prihlásenia v SSO portáli s návratom na {@code returnPath}. */
    String loginUrl(String returnPath);
}
