package com.example.crm.web.portlet;

/** Názvy request atribútov, ktorými web vrstva odovzdáva JSP tagom kontext „portletu“. */
public final class PortletRequestAttributes {

    /**
     * Base path aktuálneho „portletu“ bez context path, napr. {@code /blacklist} alebo {@code /dashboard/C-1001}.
     * Nastavuje ho {@code LegacyPortletInterceptor} z anotácie {@code @LegacyPortlet}; čítajú ho URL tagy.
     */
    public static final String BASE_PATH = "crm.portlet.basePath";

    private PortletRequestAttributes() {
    }
}
