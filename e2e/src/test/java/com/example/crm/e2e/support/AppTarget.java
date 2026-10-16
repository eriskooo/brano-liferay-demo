package com.example.crm.e2e.support;

import java.nio.file.Path;

/**
 * Testovaný cieľ. Jediné miesto pre zámerné odchýlky medzi starou a novou appkou – všetko ostatné je spoločné.
 * Vedomá medzera: vynútený POST čitateľa sa netestuje (legacy portlet URL sa bez formulára zložiť nedá);
 * 403 v novej appke overuje docker/smoke-migration.sh.
 */
public sealed interface AppTarget permits LegacyTarget, MigratedTarget {

    /** Názov cieľa (legacy | migrated) – aj adresár artefaktov. */
    String name();

    /** URL, kde test začína po prihlásení. */
    String entryUrl();

    /** Log súbor aplikácie (audit + hygiena). */
    Path appLog();

    /** Cieľ podľa {@code e2e.target}. */
    static AppTarget of(E2eConfig config) {
        return switch (config.target()) {
            case "legacy" -> new LegacyTarget(config);
            case "migrated" -> new MigratedTarget(config);
            default -> throw new IllegalArgumentException(
                    "Neznámy cieľ e2e.target=" + config.target() + " (povolené: legacy|migrated)");
        };
    }
}
