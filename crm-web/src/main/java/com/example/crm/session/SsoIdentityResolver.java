package com.example.crm.session;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Identita podľa cookie {@code SSO_ID} – tú istú cookie číta aj stará appka, takže obe vidia toho istého operátora
 * a zákazníka. Neplatný tvar cookie = neprihlásený (hodnota ide do URL portálu aj do kľúča v Redise).
 */
@Component
public class SsoIdentityResolver implements IdentityResolver {

    static final String COOKIE = "SSO_ID";
    private static final Pattern VALID_ID = Pattern.compile("[A-Za-z0-9-]{1,64}");
    private static final Object NO_IDENTITY = new Object();

    private final SsoClient client;
    private final String loginUrl;

    public SsoIdentityResolver(SsoClient client, @Value("${crm.sso.login-url}") String loginUrl) {
        this.client = client;
        this.loginUrl = loginUrl;
    }

    @Override
    public Optional<SsoIdentity> resolve(HttpServletRequest request) {
        Object cached = request.getAttribute(SsoIdentity.REQUEST_ATTRIBUTE);
        if (cached == NO_IDENTITY) {
            return Optional.empty();
        }
        if (cached instanceof SsoIdentity identity) {
            return Optional.of(identity);
        }
        Optional<SsoIdentity> identity = ssoSessionId(request).flatMap(client::fetch);
        request.setAttribute(SsoIdentity.REQUEST_ATTRIBUTE, identity.isPresent() ? identity.get() : NO_IDENTITY);
        return identity;
    }

    @Override
    public String loginUrl(String returnPath) {
        return loginUrl + "?returnUrl=" + URLEncoder.encode(returnPath, StandardCharsets.UTF_8);
    }

    private static Optional<String> ssoSessionId(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(c -> COOKIE.equals(c.getName()))
                .map(Cookie::getValue)
                .filter(v -> v != null && VALID_ID.matcher(v).matches())
                .findFirst();
    }
}
