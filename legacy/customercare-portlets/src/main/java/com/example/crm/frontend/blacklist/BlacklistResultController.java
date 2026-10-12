package com.example.crm.frontend.blacklist;

import com.example.crm.frontend.backend.blacklist.BlacklistSearchCriteria;
import com.example.crm.frontend.backend.blacklist.BlacklistService;
import com.example.crm.frontend.backend.blacklist.BlacklistState;
import com.example.crm.frontend.base.web.BaseCrmController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.SessionAttributes;
import org.springframework.web.portlet.bind.annotation.RenderMapping;

/** „Stránka“ výsledkov: controller sa vyberá render parametrom {@code viewPage} (legacy dispatch v rámci portletu). */
@Controller
@RequestMapping(value = "VIEW", params = "viewPage=searchresult")
@SessionAttributes(BlacklistSearchController.CRITERIA)
public class BlacklistResultController extends BaseCrmController {

    private final BlacklistService blacklistService;

    @Autowired
    public BlacklistResultController(BlacklistService blacklistService) {
        this.blacklistService = blacklistService;
    }

    /** Ak kritériá v session nie sú (vypršala), vznikne prázdny objekt a stránka vráti formulár. */
    @ModelAttribute(BlacklistSearchController.CRITERIA)
    public BlacklistSearchCriteria criteria() {
        return new BlacklistSearchCriteria();
    }

    /** Tabuľka výsledkov. */
    @RenderMapping
    public String result(@ModelAttribute(BlacklistSearchController.CRITERIA) BlacklistSearchCriteria criteria,
                         Model model) {
        if (!criteria.isAnyCriterionFilled()) {
            model.addAttribute("states", BlacklistState.values());
            return "blacklist/search";
        }
        model.addAttribute("records", blacklistService.search(criteria));
        return "blacklist/searchresult";
    }
}
