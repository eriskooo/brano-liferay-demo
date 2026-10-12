package com.example.crm.web.tag;

import java.util.Optional;

import com.example.crm.acl.AclService;
import com.example.crm.acl.Privilege;
import com.example.crm.session.OperatorUser;
import com.example.crm.session.SsoIdentity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.jsp.JspException;
import jakarta.servlet.jsp.PageContext;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.support.RequestContextUtils;

/** Spoločná logika ACL tagov: aktuálny operátor zo session fasády + {@link AclService} zo Spring kontextu. */
final class AclTagSupport {

    private AclTagSupport() {
    }

    static boolean currentOperatorHas(PageContext pageContext, String privilegeType) throws JspException {
        Privilege privilege = parse(privilegeType);
        HttpServletRequest request = (HttpServletRequest) pageContext.getRequest();
        // Identitu rozpoznal interceptor (SSO) a nechal ju v requeste – tag sa SSO znova nepýta.
        Optional<OperatorUser> operator = SsoIdentity.current(request).map(SsoIdentity::operator);
        return operator.isPresent() && context(pageContext).getBean(AclService.class).hasPrivilege(operator.get(), privilege);
    }

    static String message(PageContext pageContext, String key, String argument) throws JspException {
        HttpServletRequest request = (HttpServletRequest) pageContext.getRequest();
        return context(pageContext).getMessage(key, new Object[] {argument}, "Chýba privilégium " + argument,
                RequestContextUtils.getLocale(request));
    }

    private static Privilege parse(String privilegeType) throws JspException {
        try {
            return Privilege.valueOf(privilegeType);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new JspException("Neznáme privilégium: " + privilegeType);
        }
    }

    private static WebApplicationContext context(PageContext pageContext) throws JspException {
        WebApplicationContext context = RequestContextUtils.findWebApplicationContext(
                (HttpServletRequest) pageContext.getRequest(), pageContext.getServletContext());
        if (context == null) {
            throw new JspException("ACL tag: nenašiel sa Spring WebApplicationContext");
        }
        return context;
    }
}
