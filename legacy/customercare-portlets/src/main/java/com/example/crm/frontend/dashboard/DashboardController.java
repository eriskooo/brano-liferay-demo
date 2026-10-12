package com.example.crm.frontend.dashboard;

import com.example.crm.frontend.backend.deactivation.CustomerProduct;
import com.example.crm.frontend.backend.deactivation.CustomerProductService;
import com.example.crm.frontend.backend.deactivation.DeactivationRequest;
import com.example.crm.frontend.backend.deactivation.DeactivationRules;
import com.example.crm.frontend.backend.deactivation.DeactivationService;
import com.example.crm.frontend.backend.deactivation.DeactivationType;
import com.example.crm.frontend.base.web.BaseCrmController;
import com.example.crm.frontend.base.web.NotFoundException;
import com.example.sso.api.PortalCustomer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Collections;
import javax.portlet.ActionResponse;
import javax.portlet.ResourceResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.SessionAttributes;
import org.springframework.web.bind.support.SessionStatus;
import org.springframework.web.portlet.bind.annotation.ActionMapping;
import org.springframework.web.portlet.bind.annotation.RenderMapping;
import org.springframework.web.portlet.bind.annotation.ResourceMapping;

/**
 * Dashboard zákazníka a stránka zrušenia služby. „Stránka“ sa vyberá render parametrom {@code page}
 * (legacy používa vlastný HandlerMapping a friendly URL; tu štandardné {@code params}). Po migrácii z toho budú
 * URL cesty {@code /dashboard/{customerId}/products/{productId}/deactivation}.
 */
@Controller
@RequestMapping("VIEW")
@SessionAttributes(DashboardController.FORM)
public class DashboardController extends BaseCrmController {

    static final String FORM = "deactivationForm";

    private final CustomerProductService products;
    private final DeactivationService deactivations;
    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final DeactivationFormValidator validator;

    @Autowired
    public DashboardController(CustomerProductService products, DeactivationService deactivations, Clock clock,
                               ObjectMapper objectMapper) {
        this.products = products;
        this.deactivations = deactivations;
        this.clock = clock;
        this.objectMapper = objectMapper;
        this.validator = new DeactivationFormValidator(clock);
    }

    /**
     * Pasca z PoC logu: bez tejto metódy JSP s {@code <form:form modelAttribute="deactivationForm">} spadne
     * na „Neither BindingResult nor plain target object…“. Pri prepise portletov sa ľahko stratí.
     */
    @ModelAttribute(FORM)
    public DeactivationForm form() {
        DeactivationForm form = new DeactivationForm();
        form.setType(DeactivationType.CUSTOMER_DEACTIVATION);
        form.setNoticeDate(LocalDate.now(clock));
        return form;
    }

    /** Typy žiadosti pre radio buttony. */
    @ModelAttribute("deactivationTypes")
    public DeactivationType[] deactivationTypes() {
        return DeactivationType.values();
    }

    /** Prehľad produktov zákazníka zo SSO session. */
    @RenderMapping
    public String products(Model model) {
        PortalCustomer customer = requireCustomer();
        model.addAttribute("customer", customer);
        model.addAttribute("products", products.productsOf(customer.getCustomerId()));
        return "dashboard/products";
    }

    /** Formulár zrušenia produktu. */
    @RenderMapping(params = "page=tariffDeactivation")
    public String deactivationForm(@RequestParam("productId") String productId,
                                   @ModelAttribute(FORM) DeactivationForm form, Model model) {
        CustomerProduct product = requireProduct(productId);
        // Vedomá reprodukcia legacy: formulár žije v @SessionAttributes, takže typ a dátumy ostanú
        // z predchádzajúceho produktu. Migrácia to opraví (nový formulár pre každý produkt).
        form.setProductId(product.getId());
        model.addAttribute("product", product);
        if (form.getType() != null && form.getNoticeDate() != null) {
            model.addAttribute("earliestDate", DeactivationRules.earliestEffectiveDate(form.getType(), form.getNoticeDate()));
        }
        return "dashboard/deactivation/form";
    }

    /** AJAX: najskorší dátum účinnosti ako JSON. */
    @ResourceMapping("earliestDate")
    public void earliestDate(@RequestParam("type") DeactivationType type, @RequestParam("noticeDate") LocalDate noticeDate,
                             ResourceResponse response) throws IOException {
        LocalDate earliest = DeactivationRules.earliestEffectiveDate(type, noticeDate);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(
                Collections.singletonMap("earliestDate", earliest.toString())));
    }

    /** Odoslanie žiadosti. */
    @ActionMapping(params = "action=submit")
    public void submit(@ModelAttribute(FORM) DeactivationForm form, BindingResult result, SessionStatus status,
                       ActionResponse response) {
        CustomerProduct product = requireProduct(form.getProductId());
        validator.validate(form, result);
        if (result.hasErrors()) {
            response.setRenderParameter("page", "tariffDeactivation");
            response.setRenderParameter("productId", product.getId());
            return;
        }
        DeactivationRequest request = deactivations.submit(product, form.getType(), form.getNoticeDate(),
                form.getEffectiveDate(), operatorName());
        status.setComplete();
        getSessionManager().pushInfo("dashboard.deactivation.submitted");
        response.setRenderParameter("page", "tariffDeactivationDone");
        response.setRenderParameter("requestId", String.valueOf(request.getId()));
    }

    /** Potvrdenie žiadosti (len žiadosť aktuálneho zákazníka). */
    @RenderMapping(params = "page=tariffDeactivationDone")
    public String done(@RequestParam("requestId") long requestId, Model model) {
        DeactivationRequest request = deactivations.find(requestId).orElse(null);
        if (request == null || !request.getCustomerId().equals(requireCustomer().getCustomerId())) {
            throw new NotFoundException("žiadosť " + requestId);
        }
        model.addAttribute("deactivationRequest", request);
        // Číslo ako text: MessageFormat by inak číslo naformátoval podľa locale (1 000).
        model.addAttribute("requestNumber", String.valueOf(request.getId()));
        return "dashboard/deactivation/done";
    }

    private CustomerProduct requireProduct(String productId) {
        CustomerProduct product = products.find(requireCustomer().getCustomerId(), productId).orElse(null);
        if (product == null) {
            throw new NotFoundException("produkt " + productId + " aktuálneho zákazníka");
        }
        return product;
    }
}
