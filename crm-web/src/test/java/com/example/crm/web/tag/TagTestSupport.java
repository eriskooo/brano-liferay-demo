package com.example.crm.web.tag;

import java.util.Locale;

import com.example.crm.acl.InMemoryAclService;
import com.example.crm.session.OperatorUser;
import com.example.crm.session.SsoIdentity;
import com.example.crm.web.portlet.PortletRequestAttributes;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockPageContext;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.StaticWebApplicationContext;

/** Minimálne prostredie JSP stránky pre unit testy tagov (bez servera a bez Jasperu). */
final class TagTestSupport {

    final MockServletContext servletContext = new MockServletContext();
    final StaticWebApplicationContext context = new StaticWebApplicationContext();
    final MockHttpServletRequest request = new MockHttpServletRequest(servletContext);
    final MockHttpServletResponse response = new MockHttpServletResponse();
    final MockPageContext pageContext;

    TagTestSupport() {
        context.setServletContext(servletContext);
        context.registerSingleton("aclService", InMemoryAclService.class);
        context.getStaticMessageSource().addMessage("greeting", Locale.ENGLISH, "Ahoj {0}");
        context.getStaticMessageSource().addMessage("html", Locale.ENGLISH, "<b>tučné</b>");
        context.getStaticMessageSource().addMessage("acl.pageDenied", Locale.ENGLISH, "Chýba {0}");
        context.refresh();
        servletContext.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, context);

        request.setContextPath("/app");
        request.setAttribute(PortletRequestAttributes.BASE_PATH, "/blacklist");
        response.setCharacterEncoding("UTF-8");
        pageContext = new MockPageContext(servletContext, request, response);
    }

    void loginAs(String login) {
        request.setAttribute(SsoIdentity.REQUEST_ATTRIBUTE, new SsoIdentity("test", new OperatorUser(login, login), null));
    }

    String output() throws Exception {
        pageContext.getOut().flush();
        return response.getContentAsString();
    }
}
