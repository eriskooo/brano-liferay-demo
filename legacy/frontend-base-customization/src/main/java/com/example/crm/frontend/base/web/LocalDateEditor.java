package com.example.crm.frontend.base.web;

import java.beans.PropertyEditorSupport;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Editor dátumu pre portlet MVC. Spring Portlet MVC nemá {@code <mvc:annotation-driven>} s conversion service,
 * legacy preto registruje vlastné PropertyEditory v base controlleri. Spring Boot to v migrácii rieši sám.
 */
public class LocalDateEditor extends PropertyEditorSupport {

    @Override
    public void setAsText(String text) {
        if (text == null || text.trim().isEmpty()) {
            setValue(null);
            return;
        }
        try {
            setValue(LocalDate.parse(text.trim()));
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Neplatný dátum '" + text + "' (očakáva sa RRRR-MM-DD)", e);
        }
    }

    @Override
    public String getAsText() {
        Object value = getValue();
        return value == null ? "" : value.toString();
    }
}
