package com.example.crm.frontend.base.tag;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.servlet.jsp.JspException;
import javax.servlet.jsp.tagext.TagSupport;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.web.util.HtmlUtils;

/**
 * {@code <common:message key="…" [var] [includeKey]>} s {@code <common:msgarg>} – legacy i18n tag (tisíce použití).
 * Chýbajúci kľúč je viditeľný ({@code ??key??}) a zalogovaný; PoC ho ticho nahrádzal a chyby nebolo vidieť.
 */
public class MessageTag extends TagSupport {

    private static final long serialVersionUID = 1L;
    private static final Log LOG = LogFactory.getLog(MessageTag.class);

    private final List<Object> arguments = new ArrayList<>();
    private String key;
    private String var;
    private boolean includeKey;

    public void setKey(String key) {
        this.key = key;
    }

    public void setVar(String var) {
        this.var = var;
    }

    public void setIncludeKey(boolean includeKey) {
        this.includeKey = includeKey;
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
        String text = HtmlUtils.htmlEscape(resolve(), "UTF-8");
        if (var != null) {
            pageContext.setAttribute(var, text);
            return EVAL_PAGE;
        }
        try {
            pageContext.getOut().write(text);
        } catch (IOException e) {
            throw new JspException(e);
        }
        return EVAL_PAGE;
    }

    private String resolve() {
        MessageSource messages = TagBeans.get(pageContext, MessageSource.class);
        Locale locale = pageContext.getRequest().getLocale();
        try {
            String text = messages.getMessage(key, arguments.toArray(), locale);
            return includeKey ? text + " [" + key + "]" : text;
        } catch (NoSuchMessageException e) {
            LOG.warn("Chýbajúci i18n kľúč '" + key + "'");
            return "??" + key + "??";
        }
    }
}
