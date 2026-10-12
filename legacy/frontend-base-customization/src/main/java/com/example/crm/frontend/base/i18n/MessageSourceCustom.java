package com.example.crm.frontend.base.i18n;

import org.springframework.context.support.ResourceBundleMessageSource;

/**
 * Zdroj prekladov. Legacy ich číta z DB a externého CMS (vlastný MessageSource); demo z {@code messages.properties}.
 * Rozhranie (Spring {@code MessageSource}) je rovnaké, takže tag {@code common:message} sa pri zmene zdroja nemení.
 */
public class MessageSourceCustom extends ResourceBundleMessageSource {

    public MessageSourceCustom() {
        setBasename("messages");
        setDefaultEncoding("UTF-8");
        // Bez fallbacku na locale servera: rovnaký text pre každý prehliadač (demo je len po slovensky).
        setFallbackToSystemLocale(false);
    }
}
