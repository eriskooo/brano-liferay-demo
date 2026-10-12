package com.example.crm.frontend.base.tag;

import javax.portlet.BaseURL;
import javax.portlet.MimeResponse;

/** {@code <portal:actionURL>} – URL action fázy (POST formulára, zmena stavu). */
public class ActionUrlTag extends PortletUrlTagSupport {

    private static final long serialVersionUID = 1L;

    @Override
    protected BaseURL createUrl(MimeResponse response) {
        return response.createActionURL();
    }

    @Override
    protected String tagName() {
        return "portal:actionURL";
    }
}
