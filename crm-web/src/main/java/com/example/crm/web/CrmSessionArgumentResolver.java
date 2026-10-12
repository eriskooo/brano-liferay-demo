package com.example.crm.web;

import com.example.crm.session.CrmSession;
import com.example.crm.session.CrmSessions;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** {@code CrmSession session} ako parameter handler metódy – legacy volal {@code getSessionManager()} z base controllera. */
public class CrmSessionArgumentResolver implements HandlerMethodArgumentResolver {

    private final CrmSessions sessions;

    public CrmSessionArgumentResolver(CrmSessions sessions) {
        this.sessions = sessions;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return CrmSession.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        return sessions.of(webRequest.getNativeRequest(HttpServletRequest.class));
    }
}
