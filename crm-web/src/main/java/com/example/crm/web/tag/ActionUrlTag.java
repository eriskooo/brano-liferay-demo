package com.example.crm.web.tag;

import java.util.List;
import java.util.Map;

import jakarta.servlet.jsp.JspException;

/** {@code <portal:actionURL>} – URL pre POST (portletová action fáza). Segment = parameter {@code action}. */
public class ActionUrlTag extends PortletUrlTag {

    private String name;

    public void setName(String name) {
        this.name = name;
    }

    @Override
    protected String resolvePath(Map<String, List<String>> parameters) throws JspException {
        String action = take(parameters, "action");
        // V portlete 'viewPage' vyberal controller v rámci portletu; po migrácii ho určuje už samotná cesta.
        parameters.remove("viewPage");
        return segment(action != null ? action : name, "parameter 'action' (alebo atribút name)");
    }

    @Override
    protected String tagName() {
        return "<portal:actionURL>";
    }

    @Override
    public void release() {
        super.release();
        name = null;
    }
}
