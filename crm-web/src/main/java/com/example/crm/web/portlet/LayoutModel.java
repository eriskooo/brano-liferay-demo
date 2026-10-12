package com.example.crm.web.portlet;

import com.example.crm.session.CrmSession;
import com.example.crm.session.CrmSessions;
import com.example.crm.session.LastViewedRecord;
import com.example.crm.session.SsoIdentity;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.ModelAndView;

/**
 * Dáta hlavičky (náhrada portletu „header“). Používa ich interceptor pre bežné stránky aj exception handler pre
 * chybové stránky – tie vznikajú mimo postHandle, inak by hlavička ukazovala „prihláste sa“ aj prihlásenému.
 */
@Component
public class LayoutModel {

    private final CrmSessions sessions;

    public LayoutModel(CrmSessions sessions) {
        this.sessions = sessions;
    }

    /** Doplní do modelu stránky operátora, zákazníka, hlášky, posledný záznam a ladiace údaje zdieľanej session. */
    public void populate(ModelAndView modelAndView, HttpServletRequest request) {
        CrmSession session = sessions.of(request);
        modelAndView.addObject("layoutOperator", session.getOperator().orElse(null));
        modelAndView.addObject("layoutCustomer", session.getCustomer().orElse(null));
        modelAndView.addObject("layoutMessages", session.popMessages());
        modelAndView.addObject("layoutLastViewed",
                session.getGlobalAttribute(LastViewedRecord.KEY, LastViewedRecord.class).orElse(null));
        modelAndView.addObject("layoutLegacyFlag", sessions.legacyReadSource().orElse("PORTLET"));
        modelAndView.addObject("layoutSsoId", SsoIdentity.current(request).map(SsoIdentity::ssoSessionId).orElse(null));
    }
}
