package com.example.crm.web.tag;

import jakarta.servlet.jsp.JspException;
import jakarta.servlet.jsp.tagext.TagSupport;

/**
 * {@code <portal:param name value>} – pridá parameter rodičovskému URL tagu.
 * Legacy atribúty {@code codebook} a {@code var} demo nepodporuje (TLD ich nemá → JSP s nimi zlyhá pri kompilácii, nie potichu).
 */
public class UrlParamTag extends TagSupport {

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
        PortletUrlTag parent = (PortletUrlTag) findAncestorWithClass(this, PortletUrlTag.class);
        if (parent == null) {
            throw new JspException("<portal:param> musí byť vnorený v <portal:actionURL|renderURL|resourceURL>");
        }
        parent.addParameter(name, value);
        return SKIP_BODY;
    }
}
