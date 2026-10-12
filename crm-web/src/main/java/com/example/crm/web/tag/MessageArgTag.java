package com.example.crm.web.tag;

import jakarta.servlet.jsp.JspException;
import jakarta.servlet.jsp.tagext.TagSupport;

/** {@code <common:msgarg value>} – argument pre rodičovský {@code <common:message>}. */
public class MessageArgTag extends TagSupport {

    private Object value;

    public void setValue(Object value) {
        this.value = value;
    }

    @Override
    public int doStartTag() throws JspException {
        MessageTag parent = (MessageTag) findAncestorWithClass(this, MessageTag.class);
        if (parent == null) {
            throw new JspException("<common:msgarg> musí byť vnorený v <common:message>");
        }
        parent.addArgument(value);
        return SKIP_BODY;
    }
}
