package com.example.crm.frontend.base.tag;

import java.io.IOException;
import java.util.Locale;
import javax.servlet.jsp.JspException;
import javax.servlet.jsp.tagext.TagSupport;
import org.springframework.context.MessageSource;
import org.springframework.web.util.HtmlUtils;

/**
 * {@code <common:aclPageFilterTag privilegeType="…" wireframe="…"/>} – bez privilégia vypíše 403 a zastaví render
 * zvyšku JSP ({@code SKIP_PAGE}). Atribút {@code wireframe} sa prijíma kvôli kompatibilite JSP a ignoruje sa
 * (v legacy riadil vzhľad „kostry“ stránky v theme).
 */
public class AclPageFilterTag extends TagSupport {

    private static final long serialVersionUID = 1L;

    private String privilegeType;
    private boolean denied;

    public void setPrivilegeType(String privilegeType) {
        this.privilegeType = privilegeType;
    }

    public void setWireframe(String wireframe) {
        // zámerne ignorované – viď JavaDoc triedy
    }

    @Override
    public int doStartTag() throws JspException {
        denied = !AclFilterTag.allowed(pageContext, privilegeType);
        return SKIP_BODY;
    }

    @Override
    public int doEndTag() throws JspException {
        if (!denied) {
            return EVAL_PAGE;
        }
        MessageSource messages = TagBeans.get(pageContext, MessageSource.class);
        Locale locale = pageContext.getRequest().getLocale();
        String text = messages.getMessage("error.403", new Object[] {privilegeType}, "403", locale);
        try {
            pageContext.getOut().write("<div class=\"portlet-msg-error\">" + HtmlUtils.htmlEscape(text, "UTF-8") + "</div>");
        } catch (IOException e) {
            throw new JspException(e);
        }
        return SKIP_PAGE;
    }
}
