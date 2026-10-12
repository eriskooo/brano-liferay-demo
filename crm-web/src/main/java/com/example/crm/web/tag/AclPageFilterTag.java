package com.example.crm.web.tag;

import java.io.IOException;

import jakarta.servlet.jsp.JspException;
import jakarta.servlet.jsp.tagext.TagSupport;
import org.springframework.web.util.HtmlUtils;

/**
 * {@code <common:aclPageFilterTag privilegeType="..." wireframe="..."/>} – bez privilégia zastaví vykresľovanie
 * zvyšku stránky (rovnako ako legacy). Je to druhá línia obrany; prvá je {@code @RequiresPrivilege} na controlleri.
 */
public class AclPageFilterTag extends TagSupport {

    private String privilegeType;

    public void setPrivilegeType(String privilegeType) {
        this.privilegeType = privilegeType;
    }

    /** V legacy odkaz na číslo obrazovky v UI návrhu – pre beh nemá význam, prijíma sa kvôli kompatibilite JSP. */
    public void setWireframe(String ignored) {
        // zámerne prázdne
    }

    @Override
    public int doEndTag() throws JspException {
        if (AclTagSupport.currentOperatorHas(pageContext, privilegeType)) {
            return EVAL_PAGE;
        }
        try {
            pageContext.getOut().write("<p class=\"acl-denied\">"
                    + HtmlUtils.htmlEscape(AclTagSupport.message(pageContext, "acl.pageDenied", privilegeType), "UTF-8") + "</p>");
        } catch (IOException e) {
            throw new JspException(e);
        }
        return SKIP_PAGE;
    }
}
