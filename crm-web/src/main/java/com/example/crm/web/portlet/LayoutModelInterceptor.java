package com.example.crm.web.portlet;

import com.example.crm.session.CrmSession;
import com.example.crm.session.CrmSessions;
import com.example.crm.session.LastViewedRecord;
import com.example.crm.session.SsoIdentity;
import com.example.crm.web.WebRequests;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;

/**
 * Dáta pre hlavičku – v Liferay ich vykresľoval portlet {@code header} vložený z theme. Hlášky sa vyberajú len pri
 * skutočnom vykreslení stránky (nie pri redirecte ani AJAX fragmente), inak by sa stratili skôr, než ich uvidíš.
 */
public class LayoutModelInterceptor implements HandlerInterceptor {

    private final CrmSessions sessions;

    public LayoutModelInterceptor(CrmSessions sessions) {
        this.sessions = sessions;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
                           ModelAndView modelAndView) {
        if (modelAndView == null || WebRequests.isAjax(request) || isRedirect(modelAndView)) {
            return;
        }
        CrmSession session = sessions.of(request);
        modelAndView.addObject("layoutOperator", session.getOperator().orElse(null));
        modelAndView.addObject("layoutCustomer", session.getCustomer().orElse(null));
        modelAndView.addObject("layoutMessages", session.popMessages());
        modelAndView.addObject("layoutLastViewed",
                session.getGlobalAttribute(LastViewedRecord.KEY, LastViewedRecord.class).orElse(null));
        modelAndView.addObject("layoutLegacyFlag", sessions.legacyReadSource().orElse("PORTLET"));
        modelAndView.addObject("layoutSsoId", SsoIdentity.current(request).map(SsoIdentity::ssoSessionId).orElse(null));
    }

    private static boolean isRedirect(ModelAndView modelAndView) {
        String viewName = modelAndView.getViewName();
        return (viewName != null && viewName.startsWith("redirect:")) || modelAndView.getView() instanceof RedirectView;
    }
}
