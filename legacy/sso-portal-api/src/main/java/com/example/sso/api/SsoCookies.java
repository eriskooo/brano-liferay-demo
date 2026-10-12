package com.example.sso.api;

import java.util.regex.Pattern;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

/** Čítanie cookie SSO portálu. Používa ho klient portálu aj CRM (kľúč zdieľanej session v Redise). */
public final class SsoCookies {

    // Hodnota ide do URL portálu aj do kľúča v Redise – iné znaky by umožnili podvrhnúť cestu alebo kľúč.
    private static final Pattern VALID_ID = Pattern.compile("[A-Za-z0-9-]{1,64}");

    private SsoCookies() {
    }

    /** Vráti ID session portálu z cookie {@code SSO_ID}, alebo {@code null}, ak chýba alebo má neplatný tvar. */
    public static String readSessionId(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (PortalSessionManager.SSO_COOKIE.equals(cookie.getName())) {
                String value = cookie.getValue();
                return value != null && VALID_ID.matcher(value).matches() ? value : null;
            }
        }
        return null;
    }
}
