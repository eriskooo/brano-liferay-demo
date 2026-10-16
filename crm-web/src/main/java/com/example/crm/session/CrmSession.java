package com.example.crm.session;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;

/**
 * Session fasáda – kontrakt legacy {@code SessionManagerCustom}, nová implementácia. Identitu vlastní SSO portál
 * (setter nie je), zdieľané kľúče sú v Redise spolu so starou appkou, ostatné v HttpSession novej appky.
 */
public interface CrmSession {

    Optional<OperatorUser> getOperator();

    /** Login operátora pre audit (displayName je pre UI); „?“ ak identita chýba – handler ju má zaručenú cez ACL. */
    default String operatorLogin() {
        return getOperator().map(OperatorUser::login).orElse("?");
    }

    Optional<CustomerContext> getCustomer();

    void setGlobalAttribute(String key, Serializable value);

    <T> Optional<T> getGlobalAttribute(String key, Class<T> type);

    void removeGlobalAttribute(String key);

    void pushInfo(String messageKey);

    void pushError(String messageKey);

    /** Vráti a odstráni čakajúce hlášky – zobrazia sa práve raz (aj cez redirect, aj z druhej appky). */
    List<UiMessage> popMessages();
}
