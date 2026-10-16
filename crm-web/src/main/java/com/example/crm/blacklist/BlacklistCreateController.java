package com.example.crm.blacklist;

import java.time.Clock;
import java.time.LocalDate;

import com.example.crm.acl.Privilege;
import com.example.crm.acl.RequiresPrivilege;
import com.example.crm.session.CrmSession;
import com.example.crm.web.portlet.LegacyPortlet;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Nový záznam. Legacy: render {@code create}, action {@code createAction}, resource {@code refreshCreateConditionTypes}. */
@Controller
@LegacyPortlet("/blacklist")
@RequestMapping("/blacklist")
@RequiresPrivilege(Privilege.BLACKLIST_MODIFY)
public class BlacklistCreateController {

    static final String CREATE_MODEL = "createForm";

    private final BlacklistService service;
    private final Clock clock;
    private final AuditLog audit;

    public BlacklistCreateController(BlacklistService service, Clock clock, AuditLog audit) {
        this.service = service;
        this.clock = clock;
        this.audit = audit;
    }

    /**
     * {@code @ModelAttribute} metóda na úrovni triedy sa volá pred KAŽDÝM handlerom tohto controllera:
     * GET dostane predvyplnený formulár, POST naň naviaže odoslané hodnoty. Presne takto to mal legacy portlet.
     * Keď ju pri migrácii niekto „zabudne“ presunúť, {@code <form:form modelAttribute="createForm">} spadne
     * s „Neither BindingResult nor plain target object…“ – to je chyba z logu predchádzajúceho PoC.
     */
    @ModelAttribute(CREATE_MODEL)
    public BlacklistCreateForm createForm() {
        BlacklistCreateForm form = new BlacklistCreateForm();
        form.setValidFrom(LocalDate.now(clock));
        return form;
    }

    @ModelAttribute("stateReasons")
    public StateReason[] stateReasons() {
        return StateReason.values();
    }

    @ModelAttribute("identifierTypes")
    public IdentifierType[] identifierTypes() {
        return IdentifierType.values();
    }

    @GetMapping("/create")
    public String createPage(@ModelAttribute(CREATE_MODEL) BlacklistCreateForm form, Model model) {
        model.addAttribute("conditionTypes", service.allowedConditionTypes(form.getStateReason()));
        return "blacklist/create";
    }

    /** Resource fáza → AJAX: po zmene dôvodu vráti HTML fragment so zoznamom povolených podmienok. */
    @GetMapping("/resource/refreshCreateConditionTypes")
    public String refreshConditionTypes(@RequestParam(required = false) StateReason stateReason, Model model) {
        model.addAttribute("conditionTypes", service.allowedConditionTypes(stateReason));
        return "blacklist/fragments/conditionTypes";
    }

    @PostMapping("/createAction")
    public String create(@Valid @ModelAttribute(CREATE_MODEL) BlacklistCreateForm form, BindingResult result,
                         Model model, CrmSession session) {
        if (form.getStateReason() != null && form.getConditionType() != null
                && !service.allowedConditionTypes(form.getStateReason()).contains(form.getConditionType())) {
            // Pravidlo závisí od backendu (povolené kombinácie) – preto nie je v Bean Validation, ale tu.
            result.rejectValue("conditionType", "blacklist.create.conditionNotAllowed");
        }
        if (result.hasErrors()) {
            model.addAttribute("conditionTypes", service.allowedConditionTypes(form.getStateReason()));
            return "blacklist/create";
        }
        BlacklistRecord created = service.create(form.toCommand());
        audit.recordCreated(created.getId(), session.operatorLogin());
        session.pushInfo("blacklist.create.created");
        return "redirect:/blacklist/detail?detailId=" + created.getId();
    }
}
