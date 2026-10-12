package com.example.crm.blacklist;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;

/** Formulár zmeny stavu/platnosti. Bean Validation nahrádza legacy vlastnú triedu Validator. */
public class BlacklistChangeForm {

    @NotNull(message = "{validation.required}")
    private BlacklistState state;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate validTo;

    public static BlacklistChangeForm from(BlacklistRecord record) {
        BlacklistChangeForm form = new BlacklistChangeForm();
        form.state = record.getState();
        form.validTo = record.getValidTo();
        return form;
    }

    public BlacklistState getState() { return state; }
    public void setState(BlacklistState state) { this.state = state; }
    public LocalDate getValidTo() { return validTo; }
    public void setValidTo(LocalDate validTo) { this.validTo = validTo; }
}
