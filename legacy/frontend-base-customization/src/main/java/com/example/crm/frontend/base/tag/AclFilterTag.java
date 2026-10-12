package com.example.crm.frontend.base.tag;

import com.example.crm.frontend.base.acl.OperatorAcl;
import javax.servlet.jsp.JspException;
import javax.servlet.jsp.tagext.TagSupport;

/** {@code <common:aclFilter privilegeType="…">} – telo len pre operátora s privilégiom (napr. tlačidlá zmeny). */
public class AclFilterTag extends TagSupport {

    private static final long serialVersionUID = 1L;

    private String privilegeType;

    public void setPrivilegeType(String privilegeType) {
        this.privilegeType = privilegeType;
    }

    @Override
    public int doStartTag() throws JspException {
        return allowed(pageContext, privilegeType) ? EVAL_BODY_INCLUDE : SKIP_BODY;
    }

    static boolean allowed(javax.servlet.jsp.PageContext pageContext, String privilegeType) throws JspException {
        try {
            return TagBeans.get(pageContext, OperatorAcl.class).hasPrivilege(privilegeType);
        } catch (IllegalArgumentException e) {
            throw new JspException(e.getMessage(), e);
        }
    }
}
