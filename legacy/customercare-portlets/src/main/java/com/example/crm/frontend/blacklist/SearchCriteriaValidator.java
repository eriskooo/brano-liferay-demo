package com.example.crm.frontend.blacklist;

import com.example.crm.frontend.backend.blacklist.BlacklistSearchCriteria;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

/** Vlastný Spring {@link Validator} ako v legacy; po migrácii ho nahradí Bean Validation ({@code @AssertTrue}). */
public class SearchCriteriaValidator implements Validator {

    @Override
    public boolean supports(Class<?> type) {
        return BlacklistSearchCriteria.class.isAssignableFrom(type);
    }

    @Override
    public void validate(Object target, Errors errors) {
        if (!((BlacklistSearchCriteria) target).isAnyCriterionFilled()) {
            errors.reject("blacklist.search.noCriteria");
        }
    }
}
