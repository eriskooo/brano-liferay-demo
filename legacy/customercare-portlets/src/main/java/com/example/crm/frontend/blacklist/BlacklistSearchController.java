package com.example.crm.frontend.blacklist;

import com.example.crm.frontend.backend.blacklist.BlacklistSearchCriteria;
import com.example.crm.frontend.backend.blacklist.BlacklistService;
import com.example.crm.frontend.backend.blacklist.BlacklistState;
import com.example.crm.frontend.base.web.BaseCrmController;
import javax.portlet.ActionResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.SessionAttributes;
import org.springframework.web.bind.support.SessionStatus;
import org.springframework.web.portlet.bind.annotation.ActionMapping;
import org.springframework.web.portlet.bind.annotation.RenderMapping;

/**
 * Default controller portletu blacklist (bez {@code viewPage}). Legacy vzory, ktoré migrácia mení:
 * action nemá výstup a len nastaví render parametre (→ POST + redirect), kritériá žijú v {@code @SessionAttributes}
 * v PortletSession (→ HttpSession), validačné chyby prenesie do renderu Spring cez „implicit model“ v session.
 */
@Controller
@RequestMapping("VIEW")
@SessionAttributes(BlacklistSearchController.CRITERIA)
public class BlacklistSearchController extends BaseCrmController {

    static final String CRITERIA = "searchCriteria";

    private final BlacklistService blacklistService;
    private final SearchCriteriaValidator validator = new SearchCriteriaValidator();

    @Autowired
    public BlacklistSearchController(BlacklistService blacklistService) {
        this.blacklistService = blacklistService;
    }

    /** Prázdne kritériá – volá sa len vtedy, keď ešte nie sú v session. */
    @ModelAttribute(CRITERIA)
    public BlacklistSearchCriteria criteria() {
        return new BlacklistSearchCriteria();
    }

    /** Stavy pre select. */
    @ModelAttribute("states")
    public BlacklistState[] states() {
        return BlacklistState.values();
    }

    /** Formulár vyhľadávania. */
    @RenderMapping
    public String search() {
        return "blacklist/search";
    }

    /** Vyhľadanie – výsledok ukáže render s {@code viewPage=searchresult}. */
    @ActionMapping(params = "action=searchAction")
    public void searchAction(@ModelAttribute(CRITERIA) BlacklistSearchCriteria criteria, BindingResult result,
                             ActionResponse response) {
        validator.validate(criteria, result);
        if (result.hasErrors()) {
            response.setRenderParameter("render", "search");
            return;
        }
        getSessionManager().setGlobalAttribute(LastSearchCriteria.KEY, new LastSearchCriteria(criteria.getFirstName(),
                criteria.getLastName(), criteria.getIdentifierValue(),
                criteria.getState() == null ? null : criteria.getState().name()));
        if (blacklistService.search(criteria).isEmpty()) {
            getSessionManager().pushInfo("blacklist.search.noResult");
            response.setRenderParameter("render", "search");
            return;
        }
        response.setRenderParameter("viewPage", "searchresult");
    }

    /** Vymazanie kritérií. */
    @ActionMapping(params = "action=clearFieldsAction")
    public void clearFields(SessionStatus status, ActionResponse response) {
        status.setComplete();
        response.setRenderParameter("render", "search");
    }

    /** Prechod na nový záznam – action, ktorá len presmeruje na iný render (legacy „goto“ akcia). */
    @ActionMapping(params = "action=gotoCreateAction")
    public void gotoCreate(ActionResponse response) {
        response.setRenderParameter("viewPage", "create");
    }
}
