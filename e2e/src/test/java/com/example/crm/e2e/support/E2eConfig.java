package com.example.crm.e2e.support;

import java.nio.file.Path;

/**
 * Nastavenie behu zo system properties (-De2e.*). Defaulty = lokálny Docker stack; na reálnom projekte sa mení
 * len toto (URL, JDBC, cesta k logom) – testy a page objects ostávajú.
 */
public record E2eConfig(String target, String ssoUrl, String legacyUrl, String migratedUrl, String jdbcUrl,
                        String jdbcUser, String jdbcPassword, Path logsDir, String browser, boolean headed,
                        Path seedScript) {

    /** Načíta nastavenie zo system properties s lokálnymi defaultmi. */
    public static E2eConfig fromSystemProperties() {
        return new E2eConfig(
                prop("e2e.target", "legacy"),
                prop("e2e.ssoUrl", "http://localhost:8000/sso/login"),
                prop("e2e.legacyUrl", "http://localhost:8080/web/crm/blacklist"),
                prop("e2e.migratedUrl", "http://localhost:8000/crm/blacklist/search"),
                prop("e2e.jdbcUrl", "jdbc:postgresql://localhost:5433/crm"),
                prop("e2e.jdbcUser", "postgres"),
                prop("e2e.jdbcPassword", "postgres"),
                Path.of(prop("e2e.logsDir", "../docker/logs")),
                prop("e2e.browser", "chrome"),
                Boolean.parseBoolean(prop("e2e.headed", "false")),
                Path.of(prop("e2e.seedScript", "../docker/postgres/init/02-blacklist-seed.sql")));
    }

    private static String prop(String key, String defaultValue) {
        return System.getProperty(key, defaultValue);
    }
}
