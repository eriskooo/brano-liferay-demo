package com.example.crm.frontend.base.tag;

import javax.servlet.jsp.JspException;
import javax.servlet.jsp.tagext.TagSupport;

/** {@code <portal:param name="…" value="…"/>} – parameter nadradeného URL tagu. */
public class ParamTag extends TagSupport {

    private static final long serialVersionUID = 1L;

    private String name;
    private String value;

    public void setName(String name) {
        this.name = name;
    }

    public void setValue(String value) {
        this.value = value;
    }

    @Override
    public int doStartTag() throws JspException {
        PortletUrlTagSupport url = (PortletUrlTagSupport) findAncestorWithClass(this, PortletUrlTagSupport.class);
        if (url == null) {
            throw new JspException("<portal:param> musí byť vnútri <portal:actionURL|renderURL|resourceURL>");
        }
        url.addParameter(name, value);
        return SKIP_BODY;
    }
}
