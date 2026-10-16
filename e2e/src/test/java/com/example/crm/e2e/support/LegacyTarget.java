package com.example.crm.e2e.support;

import java.nio.file.Path;

/** Stará Liferay appka. */
public record LegacyTarget(E2eConfig config) implements AppTarget {

    @Override
    public String name() {
        return "legacy";
    }

    /** Liferay priamo (:8080) – cez nginx (:8000) by strangler presmeroval blacklist na novú appku. */
    @Override
    public String entryUrl() {
        return config.legacyUrl();
    }

    /** Vlastný log portletu (log4j.xml vo WAR-e), nie celý log portálu – šum Liferay sa nemieša do hygieny. */
    @Override
    public Path appLog() {
        return config.logsDir().resolve("liferay").resolve("customercare-portlet.log");
    }
}
