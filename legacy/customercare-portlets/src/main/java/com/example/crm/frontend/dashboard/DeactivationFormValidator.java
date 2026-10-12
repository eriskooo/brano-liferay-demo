package com.example.crm.frontend.dashboard;

import com.example.crm.frontend.backend.deactivation.DeactivationRules;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

/** Vlastný validátor formulára zrušenia (legacy vzor). Pravidlo dátumu berie z {@link DeactivationRules}. */
public class DeactivationFormValidator implements Validator {

    private static final String REQUIRED = "validation.required";

    private final Clock clock;

    public DeactivationFormValidator(Clock clock) {
        this.clock = clock;
    }

    @Override
    public boolean supports(Class<?> type) {
        return DeactivationForm.class.isAssignableFrom(type);
    }

    @Override
    public void validate(Object target, Errors errors) {
        DeactivationForm form = (DeactivationForm) target;
        require(errors, "type", form.getType());
        require(errors, "noticeDate", form.getNoticeDate());
        require(errors, "effectiveDate", form.getEffectiveDate());
        if (form.getNoticeDate() != null && form.getNoticeDate().isAfter(LocalDate.now(clock))) {
            errors.rejectValue("noticeDate", "validation.noticeInFuture");
        }
        if (form.getType() != null && form.getNoticeDate() != null && form.getEffectiveDate() != null) {
            LocalDate earliest = DeactivationRules.earliestEffectiveDate(form.getType(), form.getNoticeDate());
            if (form.getEffectiveDate().isBefore(earliest)) {
                errors.rejectValue("effectiveDate", "validation.effectiveTooEarly", new Object[] {earliest.toString()}, null);
            }
        }
    }

    private static void require(Errors errors, String field, Object value) {
        if (value == null && !errors.hasFieldErrors(field)) {
            errors.rejectValue(field, REQUIRED);
        }
    }
}
