package com.example.crm.frontend.base.tag;

import javax.servlet.jsp.PageContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

/** Tagy nie sú Spring beany – beany si berú z root kontextu WAR-u (ContextLoaderListener). */
final class TagBeans {

    private TagBeans() {
    }

    /** Bean daného typu z root {@code WebApplicationContext}. */
    static <T> T get(PageContext pageContext, Class<T> type) {
        return WebApplicationContextUtils.getRequiredWebApplicationContext(pageContext.getServletContext()).getBean(type);
    }
}
