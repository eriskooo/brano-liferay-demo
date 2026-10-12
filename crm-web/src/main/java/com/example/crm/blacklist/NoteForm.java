package com.example.crm.blacklist;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class NoteForm {

    @NotBlank(message = "{validation.required}")
    @Size(max = 500, message = "{validation.maxLength}")
    private String text;

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
}
