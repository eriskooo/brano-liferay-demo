package com.example.crm.frontend.base.tag;

import javax.portlet.BaseURL;
import javax.portlet.MimeResponse;

/** {@code <portal:renderURL>} – URL render fázy (odkaz na inú „stránku“ portletu). */
public class RenderUrlTag extends PortletUrlTagSupport {

    private static final long serialVersionUID = 1L;

    @Override
    protected BaseURL createUrl(MimeResponse response) {
        return response.createRenderURL();
    }

    @Override
    protected String tagName() {
        return "portal:renderURL";
    }
}
