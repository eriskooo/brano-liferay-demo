package com.example.sso.api;

/**
 * Service locator ako v legacy: CRM si implementáciu API nevyberá samo, dostane ju od prostredia.
 * Tu podľa system properties (Docker: {@code CATALINA_OPTS}); testy a fake podstrčia vlastnú cez setter.
 * Pri migrácii sa locator nahradí obyčajným Spring beanom.
 */
public final class PortalServiceLocator {

    public static final String API_URL_PROPERTY = "sso.portal.api-url";
    public static final String LOGIN_URL_PROPERTY = "sso.portal.login-url";

    private static final int CONNECT_TIMEOUT_MS = 1000;
    private static final int READ_TIMEOUT_MS = 2000;

    private static volatile PortalSessionManager instance;

    private PortalServiceLocator() {
    }

    /** Vráti implementáciu API SSO portálu (pri prvom volaní ju vytvorí podľa system properties). */
    public static PortalSessionManager getPortalSessionManager() {
        PortalSessionManager current = instance;
        if (current != null) {
            return current;
        }
        synchronized (PortalServiceLocator.class) {
            if (instance == null) {
                instance = create();
            }
            return instance;
        }
    }

    /** Podstrčí implementáciu (testy, lokálny fake); {@code null} vráti locator do pôvodného stavu. */
    public static void setPortalSessionManager(PortalSessionManager manager) {
        instance = manager;
    }

    private static PortalSessionManager create() {
        String apiUrl = System.getProperty(API_URL_PROPERTY);
        if (apiUrl == null || apiUrl.trim().isEmpty()) {
            throw new IllegalStateException("Chýba system property " + API_URL_PROPERTY + " (URL API SSO portálu)");
        }
        String loginUrl = System.getProperty(LOGIN_URL_PROPERTY, "/sso/login");
        return new HttpPortalSessionManager(apiUrl.trim(), loginUrl, CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS);
    }
}
