package com.example.crm.web.portlet;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Označuje controller, ktorý nahrádza (časť) legacy portletu. V legacy mal portlet záznam v {@code portlet.xml}
 * a vlastný Spring kontext; tu je to skupina controllerov so spoločným base path – z neho skladajú URL tagy
 * {@code portal:*URL} a interceptor podľa neho vyžaduje prihláseného operátora.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface LegacyPortlet {

    /** Base path bez context path, napr. {@code /blacklist}. */
    String value();
}
