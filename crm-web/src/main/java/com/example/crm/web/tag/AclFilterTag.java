package com.example.crm.web.tag;

import jakarta.servlet.jsp.JspException;
import jakarta.servlet.jsp.tagext.TagSupport;

/** {@code <common:aclFilter privilegeType="...">telo</common:aclFilter>} – telo len pre operátora s privilégiom. */
public class AclFilterTag extends TagSupport {

    private String privilegeType;

    public void setPrivilegeType(String privilegeType) {
        this.privilegeType = privilegeType;
    }

    /** Legacy atribút pre jemnejšie ACL na zdroj – demo ho prijme a ignoruje. */
    public void setAclResource(String ignored) {
        // zámerne prázdne
    }

    @Override
    public int doStartTag() throws JspException {
        return AclTagSupport.currentOperatorHas(pageContext, privilegeType) ? EVAL_BODY_INCLUDE : SKIP_BODY;
    }
}
