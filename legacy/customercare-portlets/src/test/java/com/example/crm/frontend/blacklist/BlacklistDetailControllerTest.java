package com.example.crm.frontend.blacklist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.crm.frontend.backend.blacklist.BlacklistRecord;
import com.example.crm.frontend.backend.blacklist.BlacklistState;
import com.example.crm.frontend.backend.blacklist.Identifier;
import com.example.crm.frontend.backend.blacklist.InMemoryBlacklistService;
import com.example.crm.frontend.base.acl.AclDeniedException;
import com.example.crm.frontend.base.acl.OperatorAcl;
import com.example.crm.frontend.base.acl.Privilege;
import com.example.crm.frontend.base.session.SessionManagerCustom;
import com.example.crm.frontend.base.web.NotFoundException;
import com.example.sso.api.PortalOperator;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.portlet.MockActionResponse;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.portlet.ModelAndView;

class BlacklistDetailControllerTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-08T10:15:30Z"), ZoneOffset.UTC);

    private final InMemoryBlacklistService service = new InMemoryBlacklistService(CLOCK);
    private final SessionManagerCustom sessionManager = mock(SessionManagerCustom.class);
    private final OperatorAcl acl = mock(OperatorAcl.class);
    private final BlacklistDetailController controller = new BlacklistDetailController(service);
    private final MockActionResponse response = new MockActionResponse();

    @BeforeEach
    void wire() {
        controller.setSessionManager(sessionManager);
        controller.setOperatorAcl(acl);
        when(sessionManager.getOperator()).thenReturn(new PortalOperator("spravca", "Správca"));
    }

    @Test
    @DisplayName("Detail zobrazí záznam a zapíše ho do zdieľanej session ako posledný otvorený")
    void should_renderRecordAndShareLastViewed_when_detailOpened() {
        ExtendedModelMap model = new ExtendedModelMap();

        assertThat(controller.detail(1, model)).isEqualTo("blacklist/detail");
        assertThat(((BlacklistRecord) model.get("record")).getFullName()).isEqualTo("Peter Vzorka");
        verify(sessionManager).setGlobalAttribute(eq(LastViewedRecord.KEY),
                argThat(v -> ((LastViewedRecord) v).getId() == 1 && "Peter Vzorka".equals(((LastViewedRecord) v).getFullName())));
    }

    @Test
    @DisplayName("Neexistujúci záznam je 404")
    void should_throwNotFound_when_recordUnknown() {
        assertThatThrownBy(() -> controller.detail(999, new ExtendedModelMap())).isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("Zmena stavu s privilégiom: zmena, hláška a návrat na detail")
    void should_changeStateAndReturnToDetail_when_privileged() {
        controller.change(5, BlacklistState.INACTIVE, null, response);

        assertThat(service.findById(5).get().getState()).isEqualTo(BlacklistState.INACTIVE);
        assertThat(service.findById(5).get().getValidTo()).isEqualTo(LocalDate.of(2026, 10, 8));
        verify(sessionManager).pushInfo("blacklist.detail.changed");
        assertThat(response.getRenderParameter("viewPage")).isEqualTo("detail");
        assertThat(response.getRenderParameter("detailId")).isEqualTo("5");
    }

    @Test
    @DisplayName("Zmena bez privilégia BLACKLIST_MODIFY sa neuskutoční")
    void should_refuse_when_notPrivileged() {
        doThrow(new AclDeniedException(Privilege.BLACKLIST_MODIFY)).when(acl).checkPrivilege(Privilege.BLACKLIST_MODIFY);

        assertThatThrownBy(() -> controller.change(5, BlacklistState.INACTIVE, null, response))
                .isInstanceOf(AclDeniedException.class);
        assertThat(service.findById(5).get().getState()).isEqualTo(BlacklistState.ACTIVE);
    }

    @Test
    @DisplayName("Prázdna poznámka je chyba, vyplnená sa uloží s menom operátora")
    void should_validateAndStoreNote_when_adding() {
        controller.addNote(6, "   ", response);
        verify(sessionManager).pushError("blacklist.detail.noteEmpty");

        controller.addNote(6, "Overené telefonicky.", response);

        assertThat(service.findById(6).get().getNotes()).extracting(n -> n.getAuthor()).containsExactly("Správca");
        verify(sessionManager).pushInfo("blacklist.detail.noteAdded");
    }

    @Test
    @DisplayName("AJAX odstránenie vráti fragment so zostávajúcimi identifikátormi")
    @SuppressWarnings("unchecked")
    void should_returnFragmentWithRemaining_when_identifierRemoved() {
        ModelAndView view = controller.removeIdentifier(1, 2);

        assertThat(view.getViewName()).isEqualTo("blacklist/fragments/identifiers");
        assertThat((List<Identifier>) view.getModel().get("identifiers")).extracting(Identifier::getId).containsExactly(1L);
        assertThat(view.getModel().get("errorKey")).isNull();
    }

    @Test
    @DisplayName("Posledný identifikátor: fragment s chybovým kľúčom a nezmeneným zoznamom")
    @SuppressWarnings("unchecked")
    void should_returnErrorKey_when_removingLastIdentifier() {
        ModelAndView view = controller.removeIdentifier(4, 5);

        assertThat(view.getModel().get("errorKey")).isEqualTo("blacklist.detail.lastIdentifier");
        assertThat((List<Identifier>) view.getModel().get("identifiers")).hasSize(1);
    }

    @Test
    @DisplayName("Identifikátor iného záznamu je 404 (resolver z toho spraví HTTP 404)")
    void should_throwNotFound_when_identifierOfOtherRecord() {
        assertThatThrownBy(() -> controller.removeIdentifier(1, 3)).isInstanceOf(NotFoundException.class);
    }
}
