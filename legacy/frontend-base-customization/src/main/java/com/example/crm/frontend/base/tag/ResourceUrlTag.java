package com.example.crm.frontend.base.tag;

import javax.portlet.BaseURL;
import javax.portlet.MimeResponse;
import javax.portlet.ResourceURL;

/** {@code <portal:resourceURL id="…">} – URL resource fázy (AJAX: HTML fragment alebo JSON). */
public class ResourceUrlTag extends PortletUrlTagSupport {

    private static final long serialVersionUID = 1L;

    private String resourceId;

    // Atribút sa v JSP volá „id“ ako v legacy TLD; TagSupport už má setId pre id tagu, preto ho prepisujeme.
    @Override
    public void setId(String id) {
        this.resourceId = id;
    }

    @Override
    protected BaseURL createUrl(MimeResponse response) {
        ResourceURL url = response.createResourceURL();
        if (resourceId != null) {
            url.setResourceID(resourceId);
        }
        return url;
    }

    @Override
    protected String tagName() {
        return "portal:resourceURL";
    }
}
