package com.example.crm.e2e.support;

import java.nio.file.Path;

/** Zmigrovaná Spring Boot appka. */
public record MigratedTarget(E2eConfig config) implements AppTarget {

    @Override
    public String name() {
        return "migrated";
    }

    /** Nová URL (render fáza → GET /blacklist/search) – zámerná odchýlka, adresa portálu už neexistuje. */
    @Override
    public String entryUrl() {
        return config.migratedUrl();
    }

    @Override
    public Path appLog() {
        return config.logsDir().resolve("crm-web").resolve("crm-web.log");
    }
}
