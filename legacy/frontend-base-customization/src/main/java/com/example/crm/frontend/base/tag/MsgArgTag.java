package com.example.crm.frontend.base.tag;

import javax.servlet.jsp.JspException;
import javax.servlet.jsp.tagext.TagSupport;

/** {@code <common:msgarg value="…"/>} – argument nadradeného {@code <common:message>}. */
public class MsgArgTag extends TagSupport {

    private static final long serialVersionUID = 1L;

    private Object value;

    public void setValue(Object value) {
        this.value = value;
    }

    @Override
    public int doStartTag() throws JspException {
        MessageTag message = (MessageTag) findAncestorWithClass(this, MessageTag.class);
        if (message == null) {
            throw new JspException("<common:msgarg> musí byť vnútri <common:message>");
        }
        message.addArgument(value);
        return SKIP_BODY;
    }
}
