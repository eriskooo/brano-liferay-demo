package com.example.crm.frontend.base.web;

import com.example.crm.frontend.base.acl.AclDeniedException;
import com.example.sso.api.SsoUnavailableException;
import javax.portlet.MimeResponse;
import javax.portlet.PortletRequest;
import javax.portlet.ResourceResponse;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.validation.BindException;
import org.springframework.web.portlet.ModelAndView;
import org.springframework.web.portlet.bind.MissingPortletRequestParameterException;
import org.springframework.web.portlet.handler.SimpleMappingExceptionResolver;

/**
 * Mapovanie výnimiek na chybové JSP portletu (legacy má vlastnú podtriedu s rovnakým menom). Navyše: log
 * a HTTP status v resource fáze – AJAX volanie inak dostane 200 s HTML chybovej stránky.
 */
public class SimpleMappingExceptionResolverCustom extends SimpleMappingExceptionResolver {

    private static final Log LOG = LogFactory.getLog(SimpleMappingExceptionResolverCustom.class);

    @Override
    protected ModelAndView doResolveException(PortletRequest request, MimeResponse response, Object handler,
                                              Exception ex) {
        int status = statusOf(ex);
        if (status >= 500) {
            LOG.error("Chyba v portlete", ex);
        } else {
            LOG.warn("Požiadavku nejde spracovať (" + status + "): " + ex.getMessage());
        }
        if (ex instanceof AclDeniedException) {
            request.setAttribute("privilege", ((AclDeniedException) ex).getPrivilege().name());
        }
        if (response instanceof ResourceResponse) {
            response.setProperty(ResourceResponse.HTTP_STATUS_CODE, String.valueOf(status));
        }
        return super.doResolveException(request, response, handler, ex);
    }

    static int statusOf(Exception ex) {
        if (ex instanceof AclDeniedException) {
            return 403;
        }
        if (ex instanceof NotFoundException) {
            return 404;
        }
        if (ex instanceof TypeMismatchException || ex instanceof MissingPortletRequestParameterException
                || ex instanceof BindException) {
            return 400;
        }
        if (ex instanceof SsoUnavailableException) {
            return 503;
        }
        return 500;
    }
}
