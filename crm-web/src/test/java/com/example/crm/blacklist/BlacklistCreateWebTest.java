package com.example.crm.blacklist;

import java.time.LocalDate;

import com.example.crm.TestSessions;
import com.example.crm.session.UiMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
class BlacklistCreateWebTest {

    @Autowired
    MockMvc mvc;

    @Test
    void createFormIsPrefilledByClassLevelModelAttribute() throws Exception {
        mvc.perform(get("/blacklist/create").session(TestSessions.admin()))
                .andExpect(status().isOk())
                .andExpect(view().name("blacklist/create"))
                .andExpect(model().attribute("createForm", hasProperty("validFrom", is(LocalDate.now()))))
                .andExpect(model().attribute("conditionTypes", contains(ConditionType.values())));
    }

    @Test
    void readerCannotOpenCreate() throws Exception {
        mvc.perform(get("/blacklist/create").session(TestSessions.reader())).andExpect(status().isForbidden());
    }

    @Test
    void conditionTypesFragmentDependsOnReason() throws Exception {
        mvc.perform(get("/blacklist/resource/refreshCreateConditionTypes").param("stateReason", "FRAUD")
                        .header("X-Requested-With", "XMLHttpRequest").session(TestSessions.admin()))
                .andExpect(status().isOk())
                .andExpect(view().name("blacklist/fragments/conditionTypes"))
                .andExpect(model().attribute("conditionTypes", contains(ConditionType.BLOCK_ALL)));
    }

    @Test
    void invalidFormIsRerenderedWithErrors() throws Exception {
        mvc.perform(post("/blacklist/createAction").session(TestSessions.admin()))
                .andExpect(status().isOk())
                .andExpect(view().name("blacklist/create"))
                .andExpect(model().attributeHasFieldErrors("createForm",
                        "firstName", "lastName", "birthDate", "stateReason", "conditionType",
                        "identifierType", "identifierValue"));
    }

    @Test
    void conditionNotAllowedForReasonIsFieldError() throws Exception {
        mvc.perform(valid(post("/blacklist/createAction"), "FRAUD", "REQUIRE_DEPOSIT").session(TestSessions.admin()))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrorCode("createForm", "conditionType",
                        "blacklist.create.conditionNotAllowed"));
    }

    @Test
    void validFormCreatesRecordAndRedirectsToDetail() throws Exception {
        MockHttpSession session = TestSessions.admin();

        mvc.perform(valid(post("/blacklist/createAction")).session(session))
                .andExpect(redirectedUrlPattern("/blacklist/detail?detailId=*"));

        assertThat(TestSessions.popMessages(session))
                .containsExactly(new UiMessage(UiMessage.Level.INFO, "blacklist.create.created"));
    }

    private static MockHttpServletRequestBuilder valid(MockHttpServletRequestBuilder request) {
        return valid(request, "DEBT", "REQUIRE_DEPOSIT");
    }

    /** Každý parameter len raz – MockMvc param() hodnoty pridáva, neprepisuje. */
    private static MockHttpServletRequestBuilder valid(MockHttpServletRequestBuilder request, String reason,
                                                       String condition) {
        return request.param("firstName", "Nový").param("lastName", "Záznam").param("birthDate", "1990-01-01")
                .param("stateReason", reason).param("conditionType", condition)
                .param("validFrom", "2026-01-01").param("validTo", "")
                .param("identifierType", "COMPANY_REG_NO").param("identifierValue", "87654321");
    }
}
