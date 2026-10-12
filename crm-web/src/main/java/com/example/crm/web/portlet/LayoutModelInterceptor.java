package com.example.crm.web.portlet;

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

    private final LayoutModel layout;

    public LayoutModelInterceptor(LayoutModel layout) {
        this.layout = layout;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
                           ModelAndView modelAndView) {
        if (modelAndView == null || WebRequests.isAjax(request) || isRedirect(modelAndView)) {
            return;
        }
        layout.populate(modelAndView, request);
    }

    private static boolean isRedirect(ModelAndView modelAndView) {
        String viewName = modelAndView.getViewName();
        return (viewName != null && viewName.startsWith("redirect:")) || modelAndView.getView() instanceof RedirectView;
    }
}
