package com.example.crm.e2e.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Operátor, ktorým sa test prihlási do SSO (default {@code spravca} – všetky oprávnenia). */
@Retention(RetentionPolicy.RUNTIME)
@java.lang.annotation.Target(ElementType.METHOD)
public @interface Operator {

    /** Login operátora v SSO mocku ({@code spravca} | {@code citatel}). */
    String value();
}
