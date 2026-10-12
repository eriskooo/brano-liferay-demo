package com.example.crm.web.tag;

import java.util.List;
import java.util.Map;

import jakarta.servlet.jsp.JspException;

/** {@code <portal:renderURL>} – URL pre GET (portletová render fáza). Segment = {@code render}, inak {@code viewPage}. */
public class RenderUrlTag extends PortletUrlTag {

    /**
     * Prijíma sa kvôli kompatibilite s legacy JSP, ale ignoruje sa: kopírovanie render parametrov bolo potrebné,
     * lebo stav stránky portálu žil v URL všetkých portletov. Po migrácii má každá stránka vlastnú URL.
     */
    public void setCopyCurrentRenderParameters(boolean ignored) {
        // zámerne prázdne – viď Javadoc
    }

    @Override
    protected String resolvePath(Map<String, List<String>> parameters) throws JspException {
        String render = take(parameters, "render");
        String viewPage = take(parameters, "viewPage");
        return segment(render != null ? render : viewPage, "parameter 'render' alebo 'viewPage'");
    }

    @Override
    protected String tagName() {
        return "<portal:renderURL>";
    }
}
