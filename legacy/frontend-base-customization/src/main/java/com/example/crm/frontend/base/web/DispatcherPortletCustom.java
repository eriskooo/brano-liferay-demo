package com.example.crm.frontend.base.web;

import com.example.crm.frontend.base.acl.Privilege;
import java.io.IOException;
import javax.portlet.ActionRequest;
import javax.portlet.ActionResponse;
import javax.portlet.MimeResponse;
import javax.portlet.PortletException;
import javax.portlet.PortletRequest;
import javax.portlet.RenderRequest;
import javax.portlet.RenderResponse;
import javax.portlet.ResourceRequest;
import javax.portlet.ResourceResponse;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.web.portlet.DispatcherPortlet;

/**
 * Podtrieda Spring {@link DispatcherPortlet} ako v legacy: pred každou fázou overí prihlásenie v SSO a privilégium
 * portletu (init-param v portlet.xml; Spring ich nastaví cez settery). Pri migrácii z toho bude HandlerInterceptor
 * nad jedným DispatcherServletom.
 */
public class DispatcherPortletCustom extends DispatcherPortlet {

    static final String LOGIN_REQUIRED_VIEW = "/WEB-INF/jsp/common/login-required.jsp";
    static final String FORBIDDEN_VIEW = "/WEB-INF/jsp/error/403.jsp";
    static final String SSO_VIEW = "/WEB-INF/jsp/error/sso.jsp";

    private static final Log LOG = LogFactory.getLog(DispatcherPortletCustom.class);

    private Privilege requiredPrivilege;
    private boolean loginRequired = true;

    /** Init-param {@code requiredPrivilege} – meno {@link Privilege}; prázdne = portlet bez privilégia. */
    public void setRequiredPrivilege(String requiredPrivilege) {
        this.requiredPrivilege = requiredPrivilege == null || requiredPrivilege.trim().isEmpty()
                ? null : Privilege.valueOf(requiredPrivilege.trim());
    }

    /** Init-param {@code loginRequired} – {@code false} pre portlety viditeľné aj bez prihlásenia (hlavička). */
    public void setLoginRequired(boolean loginRequired) {
        this.loginRequired = loginRequired;
    }

    @Override
    protected void doRenderService(RenderRequest request, RenderResponse response) throws Exception {
        PortletAccessGuard guard = guard();
        AccessDecision decision = guard.decide(loginRequired, requiredPrivilege);
        switch (decision) {
            case ALLOWED:
                super.doRenderService(request, response);
                return;
            case LOGIN_REQUIRED:
                request.setAttribute("loginUrl", guard.loginUrl());
                include(LOGIN_REQUIRED_VIEW, request, response);
                return;
            case DENIED:
                request.setAttribute("privilege", requiredPrivilege.name());
                include(FORBIDDEN_VIEW, request, response);
                return;
            default:
                include(SSO_VIEW, request, response);
        }
    }

    @Override
    protected void doActionService(ActionRequest request, ActionResponse response) throws Exception {
        PortletAccessGuard guard = guard();
        AccessDecision decision = guard.decide(loginRequired, requiredPrivilege);
        if (decision == AccessDecision.ALLOWED) {
            super.doActionService(request, response);
            return;
        }
        if (decision == AccessDecision.LOGIN_REQUIRED) {
            // Action fáza môže presmerovať; render fáza nie (portál už skladá stránku) – tam ostane len odkaz.
            response.sendRedirect(guard.loginUrl());
            return;
        }
        // Handler sa nespustí; nasledujúci render zopakuje kontrolu a ukáže 403 / chybu SSO.
        LOG.warn("Action portletu " + getPortletName() + " zamietnutá: " + decision);
    }

    @Override
    protected void doResourceService(ResourceRequest request, ResourceResponse response) throws Exception {
        AccessDecision decision = guard().decide(loginRequired, requiredPrivilege);
        if (decision == AccessDecision.ALLOWED) {
            super.doResourceService(request, response);
            return;
        }
        // AJAX dostane holý status – prihlasovacia výzva vložená JS-kom do <div> by bola nezmysel.
        response.setProperty(ResourceResponse.HTTP_STATUS_CODE, String.valueOf(decision.getHttpStatus()));
    }

    /** Strážca z root kontextu (portlet kontext ho zdedí od rodiča). */
    protected PortletAccessGuard guard() {
        return getPortletApplicationContext().getBean(PortletAccessGuard.class);
    }

    /** Vloží JSP do výstupu portletu. */
    protected void include(String path, PortletRequest request, MimeResponse response)
            throws PortletException, IOException {
        getPortletContext().getRequestDispatcher(path).include(request, response);
    }
}
