package com.example.crm.frontend.blacklist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.crm.frontend.backend.blacklist.BlacklistRecord;
import com.example.crm.frontend.backend.blacklist.BlacklistSearchCriteria;
import com.example.crm.frontend.backend.blacklist.BlacklistState;
import com.example.crm.frontend.backend.blacklist.InMemoryBlacklistService;
import com.example.crm.frontend.base.acl.OperatorAcl;
import com.example.crm.frontend.base.session.SessionManagerCustom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.portlet.MockActionResponse;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.support.SimpleSessionStatus;

class BlacklistSearchControllerTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-08T10:15:30Z"), ZoneOffset.UTC);

    private final InMemoryBlacklistService service = new InMemoryBlacklistService(CLOCK);
    private final SessionManagerCustom sessionManager = mock(SessionManagerCustom.class);
    private final BlacklistSearchController search = new BlacklistSearchController(service);
    private final BlacklistResultController result = new BlacklistResultController(service);
    private final MockActionResponse response = new MockActionResponse();

    @BeforeEach
    void wire() {
        for (com.example.crm.frontend.base.web.BaseCrmController controller
                : new com.example.crm.frontend.base.web.BaseCrmController[] {search, result}) {
            controller.setSessionManager(sessionManager);
            controller.setOperatorAcl(mock(OperatorAcl.class));
        }
    }

    @Test
    @DisplayName("Default render je formulár vyhľadávania so stavmi")
    void should_renderSearchForm_when_noParams() {
        assertThat(search.search()).isEqualTo("blacklist/search");
        assertThat(search.states()).containsExactly(BlacklistState.ACTIVE, BlacklistState.INACTIVE);
        assertThat(search.criteria().isAnyCriterionFilled()).isFalse();
    }

    @Test
    @DisplayName("Úspešné hľadanie: render parameter na výsledky a kópia kritérií do zdieľanej session")
    void should_goToResultsAndShareCriteria_when_found() {
        BlacklistSearchCriteria criteria = criteria("Vzork");

        search.searchAction(criteria, bindingResult(criteria), response);

        assertThat(response.getRenderParameter("viewPage")).isEqualTo("searchresult");
        verify(sessionManager).setGlobalAttribute(eq(LastSearchCriteria.KEY),
                argThat(shared -> "Vzork".equals(((LastSearchCriteria) shared).getLastName())));
    }

    @Test
    @DisplayName("Bez kritérií: globálna chyba a návrat na formulár, nič sa nezdieľa")
    void should_rejectAndStay_when_noCriteria() {
        BlacklistSearchCriteria criteria = criteria("   ");
        BindingResult errors = bindingResult(criteria);

        search.searchAction(criteria, errors, response);

        assertThat(errors.getGlobalErrors()).extracting(e -> e.getCode()).containsExactly("blacklist.search.noCriteria");
        assertThat(response.getRenderParameter("render")).isEqualTo("search");
        verify(sessionManager, never()).setGlobalAttribute(any(), any());
    }

    @Test
    @DisplayName("Bez výsledku: informačná hláška a návrat na formulár")
    void should_pushInfo_when_nothingFound() {
        BlacklistSearchCriteria criteria = criteria("Neexistujúci");

        search.searchAction(criteria, bindingResult(criteria), response);

        verify(sessionManager).pushInfo("blacklist.search.noResult");
        assertThat(response.getRenderParameter("render")).isEqualTo("search");
    }

    @Test
    @DisplayName("Vymazanie ukončí @SessionAttributes (inak formulár visí v session navždy)")
    void should_completeSessionStatus_when_clearingFields() {
        SimpleSessionStatus status = new SimpleSessionStatus();

        search.clearFields(status, response);

        assertThat(status.isComplete()).isTrue();
        assertThat(response.getRenderParameter("render")).isEqualTo("search");
    }

    @Test
    @DisplayName("Navigačná action len nastaví render parameter (legacy vzor; po migrácii obyčajný odkaz)")
    void should_onlySetRenderParameter_when_gotoCreate() {
        search.gotoCreate(response);

        assertThat(response.getRenderParameter("viewPage")).isEqualTo("create");
    }

    @Test
    @DisplayName("Výsledky zobrazia záznamy podľa kritérií zo session")
    void should_listRecords_when_criteriaInSession() {
        ExtendedModelMap model = new ExtendedModelMap();

        String view = result.result(criteria("Vzork"), model);

        assertThat(view).isEqualTo("blacklist/searchresult");
        @SuppressWarnings("unchecked")
        List<BlacklistRecord> records = (List<BlacklistRecord>) model.get("records");
        assertThat(records).extracting(BlacklistRecord::getId).containsExactly(1L, 8L);
    }

    @Test
    @DisplayName("Výsledky bez kritérií (napr. po vypršaní session) vrátia formulár")
    void should_returnSearchForm_when_criteriaEmpty() {
        ExtendedModelMap model = new ExtendedModelMap();

        assertThat(result.result(new BlacklistSearchCriteria(), model)).isEqualTo("blacklist/search");
        assertThat(model.get("states")).isNotNull();
    }

    private static BlacklistSearchCriteria criteria(String lastName) {
        BlacklistSearchCriteria criteria = new BlacklistSearchCriteria();
        criteria.setLastName(lastName);
        return criteria;
    }

    private static BindingResult bindingResult(BlacklistSearchCriteria criteria) {
        return new BeanPropertyBindingResult(criteria, BlacklistSearchController.CRITERIA);
    }
}
