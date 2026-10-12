package com.example.crm.frontend.base.session;

import com.example.sso.api.SsoCookies;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.exception.SystemException;
import com.liferay.portal.kernel.util.WebKeys;
import com.liferay.portal.model.Layout;
import com.liferay.portal.theme.ThemeDisplay;
import com.liferay.portal.util.PortalUtil;
import javax.portlet.PortletRequest;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.portlet.context.PortletRequestAttributes;

/**
 * Liferay implementácia: request z {@link RequestContextHolder} (nastaví ho FrameworkPortlet pred každou fázou),
 * servlet request cez {@link PortalUtil} – typické legacy volanie „dostaň sa k servlet requestu“.
 * Pri migrácii celá trieda odpadne: v Spring MVC dostane controller request priamo.
 */
public class PortletRequestAwareManager implements RequestAwareManager {

    private static final Log LOG = LogFactory.getLog(PortletRequestAwareManager.class);

    @Override
    public PortletRequest getPortletRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof PortletRequestAttributes)) {
            throw new IllegalStateException("Mimo portletového requestu: RequestContextHolder neobsahuje portlet request"
                    + " (session manager funguje len vo vlákne requestu)");
        }
        return ((PortletRequestAttributes) attributes).getRequest();
    }

    @Override
    public HttpServletRequest getHttpServletRequest() {
        return PortalUtil.getOriginalServletRequest(PortalUtil.getHttpServletRequest(getPortletRequest()));
    }

    @Override
    public String getSsoSessionId() {
        return SsoCookies.readSessionId(getHttpServletRequest());
    }

    @Override
    public String getCurrentPageUrl() {
        ThemeDisplay themeDisplay = (ThemeDisplay) getPortletRequest().getAttribute(WebKeys.THEME_DISPLAY);
        if (themeDisplay == null || themeDisplay.getLayout() == null) {
            return "/";
        }
        Layout layout = themeDisplay.getLayout();
        try {
            return themeDisplay.getPathFriendlyURLPublic() + layout.getGroup().getFriendlyURL() + layout.getFriendlyURL();
        } catch (PortalException | SystemException e) {
            LOG.warn("URL aktuálnej stránky sa nedá zostaviť, návrat po prihlásení pôjde na koreň portálu", e);
            return "/";
        }
    }

    @Override
    public Object getRequestAttribute(String name) {
        return getHttpServletRequest().getAttribute(name);
    }

    @Override
    public void setRequestAttribute(String name, Object value) {
        getHttpServletRequest().setAttribute(name, value);
    }
}
