package com.example.crm.web;

import jakarta.servlet.http.HttpServletRequest;

/** Pomocné rozhodnutia o requeste, ktoré potrebuje viac miest web vrstvy. */
public final class WebRequests {

    private WebRequests() {
    }

    /** AJAX volanie (náhrada portletovej resource fázy) – chyby sa vracajú bez layoutu a bez redirectu. */
    public static boolean isAjax(HttpServletRequest request) {
        return "XMLHttpRequest".equals(request.getHeader("X-Requested-With"));
    }
}
