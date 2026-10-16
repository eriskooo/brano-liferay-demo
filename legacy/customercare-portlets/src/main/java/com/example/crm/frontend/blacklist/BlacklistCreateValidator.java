package com.example.crm.frontend.blacklist;

import com.example.crm.frontend.backend.blacklist.BlacklistService;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

/**
 * Vlastný Spring {@link Validator} (legacy vzor; po migrácii Bean Validation + jedno vlastné pravidlo).
 * Povolená podmienka pre dôvod je pravidlo backendu – validátor sa naň pýta služby, neopakuje ho.
 */
public class BlacklistCreateValidator implements Validator {

    static final int NAME_MAX_LENGTH = 100;

    private static final String REQUIRED = "validation.required";

    private final BlacklistService blacklistService;

    public BlacklistCreateValidator(BlacklistService blacklistService) {
        this.blacklistService = blacklistService;
    }

    @Override
    public boolean supports(Class<?> type) {
        return CreateForm.class.isAssignableFrom(type);
    }

    @Override
    public void validate(Object target, Errors errors) {
        CreateForm form = (CreateForm) target;
        requireText(errors, "firstName", form.getFirstName());
        requireText(errors, "lastName", form.getLastName());
        requireValue(errors, "birthDate", form.getBirthDate());
        requireValue(errors, "stateReason", form.getStateReason());
        requireValue(errors, "conditionType", form.getConditionType());
        requireValue(errors, "validFrom", form.getValidFrom());
        requireValue(errors, "identifierType", form.getIdentifierType());
        requireText(errors, "identifierValue", form.getIdentifierValue());
        // Dĺžky podľa stĺpcov zdieľanej DB – inak by dlhý vstup skončil chybou 500 namiesto chyby poľa.
        maxLength(errors, "firstName", form.getFirstName(), NAME_MAX_LENGTH);
        maxLength(errors, "lastName", form.getLastName(), NAME_MAX_LENGTH);
        if (form.getValidFrom() != null && form.getValidTo() != null && form.getValidTo().isBefore(form.getValidFrom())) {
            errors.rejectValue("validTo", "validation.validToBeforeValidFrom");
        }
        if (form.getIdentifierType() != null && hasText(form.getIdentifierValue())
                && !form.getIdentifierType().isValid(form.getIdentifierValue().trim())) {
            errors.rejectValue("identifierValue", "validation.identifierFormat");
        }
        if (form.getStateReason() != null && form.getConditionType() != null
                && !blacklistService.allowedConditionTypes(form.getStateReason()).contains(form.getConditionType())) {
            errors.rejectValue("conditionType", "validation.conditionNotAllowed");
        }
    }

    private static void requireText(Errors errors, String field, String value) {
        if (!hasText(value) && !errors.hasFieldErrors(field)) {
            errors.rejectValue(field, REQUIRED);
        }
    }

    // Pole s chybou konverzie (napr. zlý dátum) je null – nepridávame k nej ešte „povinný údaj“.
    private static void requireValue(Errors errors, String field, Object value) {
        if (value == null && !errors.hasFieldErrors(field)) {
            errors.rejectValue(field, REQUIRED);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static void maxLength(Errors errors, String field, String value, int max) {
        if (value != null && value.trim().length() > max && !errors.hasFieldErrors(field)) {
            errors.rejectValue(field, "validation.maxLength");
        }
    }
}
