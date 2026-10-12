package com.example.crm.frontend.base.tag;

import com.example.crm.frontend.base.acl.OperatorAcl;
import java.io.UnsupportedEncodingException;
import javax.servlet.jsp.PageContext;
import org.springframework.context.MessageSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockPageContext;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.GenericWebApplicationContext;

/** Page context s root Spring kontextom (ako vo WAR-e: ContextLoaderListener) bez servletového kontajnera. */
public final class TagTestSupport {

    private TagTestSupport() {
    }

    /** Page context, ktorého root kontext obsahuje daný MessageSource a ACL. */
    public static MockPageContext pageContext(MessageSource messageSource, OperatorAcl acl) {
        MockServletContext servletContext = new MockServletContext();
        GenericWebApplicationContext root = new GenericWebApplicationContext();
        root.setServletContext(servletContext);
        if (messageSource != null) {
            root.getBeanFactory().registerSingleton("messageSource", messageSource);
        }
        if (acl != null) {
            root.getBeanFactory().registerSingleton("operatorAcl", acl);
        }
        root.refresh();
        servletContext.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, root);
        MockHttpServletResponse response = new MockHttpServletResponse();
        // Portál odpovedá v UTF-8; mock má predvolene ISO-8859-1 a diakritiku by zmenil na '?'.
        response.setCharacterEncoding("UTF-8");
        return new MockPageContext(servletContext, new MockHttpServletRequest(servletContext), response);
    }

    /** Text, ktorý tagy zapísali do výstupu. */
    public static String content(PageContext pageContext) {
        try {
            return ((MockHttpServletResponse) pageContext.getResponse()).getContentAsString();
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
