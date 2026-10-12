package com.example.crm.session;

import java.io.Serializable;

/** Hláška (i18n kľúč). JSON tvar {"level","key"} je rovnaký ako v starej appke – hláška môže prejsť medzi appkami. */
public record UiMessage(Level level, String key) implements Serializable {

    public enum Level { INFO, ERROR }
}
