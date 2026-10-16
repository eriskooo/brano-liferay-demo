package com.example.crm.blacklist;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class NoteForm {

    // Rovnaká hláška ako legacy (prázdna poznámka) – odchýlku odhalil charakterizačný e2e test.
    @NotBlank(message = "{blacklist.detail.noteEmpty}")
    // 2000 = dĺžka stĺpca v DB; legacy limit nemala, staršie 500 tu bolo prísnejšie než legacy.
    @Size(max = 2000, message = "{validation.maxLength}")
    private String text;

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
}
