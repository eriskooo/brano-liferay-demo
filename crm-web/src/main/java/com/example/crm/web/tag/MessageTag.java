package com.example.crm.web.tag;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.jsp.JspException;
import jakarta.servlet.jsp.tagext.TagSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.support.RequestContextUtils;
import org.springframework.web.util.HtmlUtils;

/**
 * {@code <common:message key [var] [showMessageOnlyIfExists]>} s {@code <common:msgarg>} – rovnaké rozhranie ako legacy.
 *
 * <p>Legacy implementácia brala preklady z DB/CMS cez vlastný MessageSource. Predchádzajúci PoC tag nahradil
 * implementáciou, ktorá vypisovala kľúč – preklady tak potichu zmizli. Tu ide preklad cez Spring {@link MessageSource}
 * a chýbajúci kľúč je VIDITEĽNÝ ({@code ??kľúč??} + WARN v logu).
 */
public class MessageTag extends TagSupport {

    private static final Logger log = LoggerFactory.getLogger(MessageTag.class);

    private final List<Object> arguments = new ArrayList<>();
    private String key;
    private String var;
    private boolean showMessageOnlyIfExists;

    public void setKey(String key) {
        this.key = key;
    }

    public void setVar(String var) {
        this.var = var;
    }

    public void setShowMessageOnlyIfExists(boolean showMessageOnlyIfExists) {
        this.showMessageOnlyIfExists = showMessageOnlyIfExists;
    }

    void addArgument(Object value) {
        arguments.add(value);
    }

    @Override
    public int doStartTag() {
        arguments.clear();
        return EVAL_BODY_INCLUDE;
    }

    @Override
    public int doEndTag() throws JspException {
        HttpServletRequest request = (HttpServletRequest) pageContext.getRequest();
        WebApplicationContext context =
                RequestContextUtils.findWebApplicationContext(request, pageContext.getServletContext());
        if (context == null) {
            throw new JspException("<common:message>: nenašiel sa Spring WebApplicationContext");
        }
        String text = resolve(context, RequestContextUtils.getLocale(request));
        try {
            if (var != null) {
                pageContext.setAttribute(var, text);
            } else {
                pageContext.getOut().write(HtmlUtils.htmlEscape(text, "UTF-8"));
            }
        } catch (IOException e) {
            throw new JspException(e);
        }
        return EVAL_PAGE;
    }

    private String resolve(MessageSource messageSource, Locale locale) {
        try {
            return messageSource.getMessage(key, arguments.toArray(), locale);
        } catch (NoSuchMessageException e) {
            log.warn("Chýba i18n kľúč '{}'", key);
            return showMessageOnlyIfExists ? "" : "??" + key + "??";
        }
    }

    @Override
    public void release() {
        super.release();
        arguments.clear();
        key = null;
        var = null;
        showMessageOnlyIfExists = false;
    }
}
