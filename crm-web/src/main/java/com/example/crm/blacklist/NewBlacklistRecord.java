package com.example.crm.blacklist;

import java.time.LocalDate;

/** Príkaz na vytvorenie záznamu – backend port nezávisí od webového formulára. */
public record NewBlacklistRecord(String firstName, String lastName, LocalDate birthDate, StateReason stateReason,
                                 ConditionType conditionType, LocalDate validFrom, LocalDate validTo,
                                 IdentifierType identifierType, String identifierValue) {
}
