package com.example.crm.testsupport;

import com.example.crm.session.IdentityResolver;
import com.example.crm.session.SharedSessionStore;
import com.example.crm.session.SsoIdentity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Testovacia náhrada SSO a Redisu pre všetky {@code @SpringBootTest} (je v skenovanom balíku com.example.crm).
 * Identita = atribút session nastavený cez {@code TestSessions}; zdieľaná session = mapa v pamäti.
 * Integračný test so skutočným SSO a Redisom ju vypne: {@code crm.test.fake-infrastructure=false}.
 */
@Configuration
@ConditionalOnProperty(name = "crm.test.fake-infrastructure", havingValue = "true", matchIfMissing = true)
public class TestInfrastructure {

    public static final String TEST_IDENTITY = "TEST_IDENTITY";
    public static final InMemorySharedSessionStore STORE = new InMemorySharedSessionStore();

    @Bean
    @Primary
    public IdentityResolver testIdentityResolver() {
        return new SessionAttributeIdentityResolver();
    }

    @Bean
    @Primary
    public SharedSessionStore testSharedSessionStore() {
        return STORE;
    }

    /** Identita z atribútu HttpSession (MockMvc {@code .session(TestSessions.admin())}). */
    public static class SessionAttributeIdentityResolver implements IdentityResolver {

        @Override
        public Optional<SsoIdentity> resolve(HttpServletRequest request) {
            HttpSession session = request.getSession(false);
            Optional<SsoIdentity> identity = session != null && session.getAttribute(TEST_IDENTITY) instanceof SsoIdentity i
                    ? Optional.of(i) : Optional.empty();
            identity.ifPresent(i -> request.setAttribute(SsoIdentity.REQUEST_ATTRIBUTE, i));
            return identity;
        }

        @Override
        public String loginUrl(String returnPath) {
            return "/sso/login?returnUrl=" + java.net.URLEncoder.encode(returnPath, java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}
