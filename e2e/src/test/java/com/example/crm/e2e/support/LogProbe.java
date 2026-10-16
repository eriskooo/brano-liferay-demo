package com.example.crm.e2e.support;

import java.time.Duration;
import java.util.List;

/** Pohľad testu do logu aplikácie od značky (začiatok testu). Na reálnom projekte iná implementácia (Loki, SSH). */
public interface LogProbe {

    /** Zapamätá si aktuálny koniec logu – ďalšie metódy vidia len novšie riadky. */
    void mark();

    /** Audit udalosti od značky. */
    List<AuditEvent> auditEvents();

    /** Riadky s ERROR/FATAL alebo stacktrace od značky (hygiena). */
    List<String> errors();

    /** Všetky riadky od značky – artefakt pri zlyhaní. */
    String excerpt();

    /**
     * Počká, kým v logu bude aspoň {@code expected} udalostí (zápis cez bind mount môže mierne meškať za HTTP odpoveďou).
     * Vráti, čo našiel, aj keď ich je menej – asercia v teste tak ukáže skutočný stav.
     */
    default List<AuditEvent> awaitAuditEvents(int expected, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        List<AuditEvent> events = auditEvents();
        while (events.size() < expected && System.nanoTime() < deadline) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return events;
            }
            events = auditEvents();
        }
        return events;
    }
}
