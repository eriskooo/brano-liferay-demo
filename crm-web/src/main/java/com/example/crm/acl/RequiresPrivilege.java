package com.example.crm.acl;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Deklaratívna kontrola privilégia na controlleri alebo handler metóde (metóda má prednosť pred triedou).
 * Kontroluje ju {@code LegacyPortletInterceptor}. V legacy kontrolu robil dispatcher portletu nad ACL službou.
 */
@Documented
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPrivilege {

    Privilege value();
}
