package com.example.crm.web;

import com.example.crm.acl.AclDeniedException;
import com.example.crm.common.NotFoundException;
import com.example.crm.session.SsoUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

/** Náhrada legacy {@code SimpleMappingExceptionResolverCustom} a hooked chybových JSP portálu. */
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AclDeniedException.class)
    public ModelAndView aclDenied(AclDeniedException exception, HttpServletRequest request) {
        ModelAndView modelAndView = new ModelAndView(WebRequests.isAjax(request) ? "error/403-fragment" : "error/403");
        modelAndView.addObject("privilege", exception.getPrivilege());
        modelAndView.setStatus(HttpStatus.FORBIDDEN);
        return modelAndView;
    }

    @ExceptionHandler(NotFoundException.class)
    public ModelAndView notFound(NotFoundException exception) {
        ModelAndView modelAndView = new ModelAndView("error/404");
        modelAndView.addObject("detail", exception.getMessage());
        modelAndView.setStatus(HttpStatus.NOT_FOUND);
        return modelAndView;
    }

    @ExceptionHandler(SsoUnavailableException.class)
    public ModelAndView ssoUnavailable() {
        ModelAndView modelAndView = new ModelAndView("error/sso");
        modelAndView.setStatus(HttpStatus.SERVICE_UNAVAILABLE);
        return modelAndView;
    }
}
