package com.example.crm.web.portlet;

import com.example.crm.acl.AclDeniedException;
import com.example.crm.acl.AclService;
import com.example.crm.acl.RequiresPrivilege;
import com.example.crm.session.IdentityResolver;
import com.example.crm.session.SsoIdentity;
import com.example.crm.web.WebRequests;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Robí to, čo v legacy {@code DispatcherPortletCustom}: (1) bez identity zo SSO nepustí ďalej – stránka ide na SSO
 * login (portlet v render fáze presmerovať nevedel, servlet áno), AJAX dostane 401; (2) zverejní base path
 * „portletu“ pre URL tagy; (3) skontroluje ACL privilégium z {@link RequiresPrivilege}.
 */
public class LegacyPortletInterceptor implements HandlerInterceptor {

    private final IdentityResolver identities;
    private final AclService aclService;

    public LegacyPortletInterceptor(IdentityResolver identities, AclService aclService) {
        this.identities = identities;
        this.aclService = aclService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        LegacyPortlet portlet = AnnotatedElementUtils.findMergedAnnotation(method.getBeanType(), LegacyPortlet.class);
        if (portlet == null) {
            return true;
        }
        Optional<SsoIdentity> identity = identities.resolve(request);
        if (identity.isEmpty()) {
            if (WebRequests.isAjax(request)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            } else {
                response.sendRedirect(identities.loginUrl(returnPath(request, portlet)));
            }
            return false;
        }
        request.setAttribute(PortletRequestAttributes.BASE_PATH, portlet.value());
        RequiresPrivilege required = requiredPrivilege(method);
        if (required != null && !aclService.hasPrivilege(identity.get().operator(), required.value())) {
            throw new AclDeniedException(required.value());
        }
        return true;
    }

    /**
     * Kam sa vrátiť po prihlásení: GET na tú istú adresu; po POST (napr. vypršaná SSO session pri odosielaní formulára)
     * na stránku portletu – POST URL by po prihlásení prehliadač otvoril cez GET a dostal 405 (legacy tiež vracal stránku).
     */
    private static String returnPath(HttpServletRequest request, LegacyPortlet portlet) {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return request.getContextPath() + portlet.value();
        }
        String query = request.getQueryString();
        return request.getRequestURI() + (query == null ? "" : "?" + query);
    }

    private static RequiresPrivilege requiredPrivilege(HandlerMethod method) {
        RequiresPrivilege onMethod = method.getMethodAnnotation(RequiresPrivilege.class);
        return onMethod != null
                ? onMethod
                : AnnotatedElementUtils.findMergedAnnotation(method.getBeanType(), RequiresPrivilege.class);
    }
}
