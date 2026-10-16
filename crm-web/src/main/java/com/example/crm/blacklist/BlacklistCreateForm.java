package com.example.crm.blacklist;

import java.time.LocalDate;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * Formulár nového záznamu. Legacy mal pre formulár samostatnú triedu {@code Validator} (Spring Validator API);
 * tu sú pravidlá priamo na formulári (Jakarta Bean Validation). Krížové pravidlá sú {@code @AssertTrue} metódy –
 * chyba sa naviaže na „property“ {@code periodValid}/{@code identifierValid} a JSP ju zobrazí cez {@code form:errors}.
 */
public class BlacklistCreateForm {

    // Dĺžky podľa stĺpcov zdieľanej DB – inak by dlhý vstup skončil chybou 500 namiesto chyby poľa.
    @NotBlank(message = "{validation.required}")
    @Size(max = 100, message = "{validation.maxLength}")
    private String firstName;

    @NotBlank(message = "{validation.required}")
    @Size(max = 100, message = "{validation.maxLength}")
    private String lastName;

    @NotNull(message = "{validation.required}")
    @Past(message = "{validation.past}")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate birthDate;

    @NotNull(message = "{validation.required}")
    private StateReason stateReason;

    @NotNull(message = "{validation.required}")
    private ConditionType conditionType;

    @NotNull(message = "{validation.required}")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate validFrom;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate validTo;

    @NotNull(message = "{validation.required}")
    private IdentifierType identifierType;

    @NotBlank(message = "{validation.required}")
    private String identifierValue;

    @AssertTrue(message = "{blacklist.create.periodInvalid}")
    public boolean isPeriodValid() {
        return validFrom == null || validTo == null || !validTo.isBefore(validFrom);
    }

    @AssertTrue(message = "{blacklist.create.identifierInvalid}")
    public boolean isIdentifierValid() {
        return identifierType == null || identifierValue == null || identifierValue.isBlank()
                || identifierType.isValid(identifierValue.trim());
    }

    public NewBlacklistRecord toCommand() {
        return new NewBlacklistRecord(firstName.trim(), lastName.trim(), birthDate, stateReason, conditionType,
                validFrom, validTo, identifierType, identifierValue.trim());
    }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public LocalDate getBirthDate() { return birthDate; }
    public void setBirthDate(LocalDate birthDate) { this.birthDate = birthDate; }
    public StateReason getStateReason() { return stateReason; }
    public void setStateReason(StateReason stateReason) { this.stateReason = stateReason; }
    public ConditionType getConditionType() { return conditionType; }
    public void setConditionType(ConditionType conditionType) { this.conditionType = conditionType; }
    public LocalDate getValidFrom() { return validFrom; }
    public void setValidFrom(LocalDate validFrom) { this.validFrom = validFrom; }
    public LocalDate getValidTo() { return validTo; }
    public void setValidTo(LocalDate validTo) { this.validTo = validTo; }
    public IdentifierType getIdentifierType() { return identifierType; }
    public void setIdentifierType(IdentifierType identifierType) { this.identifierType = identifierType; }
    public String getIdentifierValue() { return identifierValue; }
    public void setIdentifierValue(String identifierValue) { this.identifierValue = identifierValue; }
}
