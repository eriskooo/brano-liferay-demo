package com.example.crm.blacklist;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NoteFormValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("Prázdna poznámka má rovnakú hlášku ako legacy (blacklist.detail.noteEmpty) – odhalil e2e test")
    void should_useLegacyNoteEmptyMessage_when_textBlank() {
        NoteForm form = new NoteForm();
        form.setText("   ");

        assertThat(templates(form)).containsExactly("{blacklist.detail.noteEmpty}");
    }

    @Test
    @DisplayName("Vyplnená poznámka je platná")
    void should_haveNoViolations_when_textFilled() {
        NoteForm form = new NoteForm();
        form.setText("Overené telefonicky.");

        assertThat(templates(form)).isEmpty();
    }

    private List<String> templates(NoteForm form) {
        return validator.validate(form).stream().map(ConstraintViolation::getMessageTemplate).toList();
    }
    @Test
    @DisplayName("Poznámka smie mať 2000 znakov (dĺžka stĺpca v DB, ako legacy), dlhšia je chyba poľa")
    void should_limitNoteToColumnLength_when_validating() {
        NoteForm atLimit = new NoteForm();
        atLimit.setText("x".repeat(2000));
        NoteForm tooLong = new NoteForm();
        tooLong.setText("x".repeat(2001));

        assertThat(templates(atLimit)).isEmpty();
        assertThat(templates(tooLong)).containsExactly("{validation.maxLength}");
    }

}
