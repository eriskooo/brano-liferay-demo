package com.example.crm.web.tag;

import java.util.List;
import java.util.Map;

import jakarta.servlet.jsp.JspException;

/** {@code <portal:resourceURL id="...">} – URL pre AJAX (portletová resource fáza) pod {@code {base}/resource/{id}}. */
public class ResourceUrlTag extends PortletUrlTag {

    private String id;

    @Override
    public void setId(String id) {
        this.id = id;
    }

    @Override
    protected String resolvePath(Map<String, List<String>> parameters) throws JspException {
        return "/resource" + segment(id, "atribút id");
    }

    @Override
    protected String tagName() {
        return "<portal:resourceURL>";
    }

    @Override
    public void release() {
        super.release();
        id = null;
    }
}
