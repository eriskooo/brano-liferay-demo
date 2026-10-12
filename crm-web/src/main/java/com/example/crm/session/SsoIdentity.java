package com.example.crm.session;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;

/** Identita z portálovej session: ID session (kľúč zdieľanej session v Redise), operátor a zákazník. */
public record SsoIdentity(String ssoSessionId, OperatorUser operator, CustomerContext customer) {

    /** Atribút requestu s výsledkom rozpoznania (cache na jeden request; čítajú ho aj JSP tagy). */
    public static final String REQUEST_ATTRIBUTE = SsoIdentity.class.getName();

    /** Identita už rozpoznaná v tomto requeste (interceptorom alebo fasádou). */
    public static Optional<SsoIdentity> current(HttpServletRequest request) {
        return request.getAttribute(REQUEST_ATTRIBUTE) instanceof SsoIdentity identity
                ? Optional.of(identity) : Optional.empty();
    }
}
