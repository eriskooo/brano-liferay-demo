package com.example.crm.frontend.base.session;

/** Úložisko session fasády. Duálna session = dve implementácie za jedným rozhraním (slide 12 prezentácie). */
public interface SessionStore {

    /** Hodnota kľúča, alebo {@code null}. */
    Object get(String key);

    /** Uloží hodnotu (vždy celú, nikdy „úpravou na mieste“ – replikované úložisko by zmenu nevidelo). */
    void put(String key, Object value);

    /** Odstráni kľúč. */
    void remove(String key);

    /** Odstráni všetky kľúče fasády. */
    void clear();
}
