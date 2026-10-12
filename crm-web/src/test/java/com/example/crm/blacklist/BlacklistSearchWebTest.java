package com.example.crm.blacklist;

import com.example.crm.TestSessions;
import com.example.crm.session.UiMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
class BlacklistSearchWebTest {

    @Autowired
    MockMvc mvc;

    @Test
    @org.junit.jupiter.api.DisplayName("Kritériá vyhľadávania sa zapíšu do zdieľanej session (JSON pre starú appku)")
    void should_shareLastCriteria_when_searchSucceeds() throws Exception {
        MockHttpSession session = TestSessions.reader();
        String ssoId = ((com.example.crm.session.SsoIdentity) session.getAttribute(
                com.example.crm.testsupport.TestInfrastructure.TEST_IDENTITY)).ssoSessionId();

        mvc.perform(post("/blacklist/searchAction").session(session).param("lastName", "vzork"));

        assertThat(com.example.crm.testsupport.TestInfrastructure.STORE.data().get(ssoId))
                .containsEntry("APP_BLACKLIST_LAST_CRITERIA",
                        "{\"firstName\":null,\"lastName\":\"vzork\",\"identifierValue\":null,\"state\":null}");
    }

    @Test
    void searchPageHasEmptyCriteria() throws Exception {
        mvc.perform(get("/blacklist/search").session(TestSessions.reader()))
                .andExpect(status().isOk())
                .andExpect(view().name("blacklist/search"))
                .andExpect(model().attributeExists("searchCriteria", "states"));
    }

    @Test
    void blankCriteriaAreValidationError() throws Exception {
        mvc.perform(post("/blacklist/searchAction").session(TestSessions.reader())
                        .param("firstName", "  ").param("lastName", "").param("identifierValue", "").param("state", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("blacklist/search"))
                .andExpect(model().attributeHasFieldErrors("searchCriteria", "anyCriterionFilled"));
    }

    @Test
    void searchRedirectsToResultsAndKeepsCriteriaInSession() throws Exception {
        MockHttpSession session = TestSessions.reader();

        mvc.perform(post("/blacklist/searchAction").session(session).param("lastName", "vzork"))
                .andExpect(redirectedUrl("/blacklist/searchresult"));
        mvc.perform(get("/blacklist/searchresult").session(session))
                .andExpect(view().name("blacklist/searchresult"))
                .andExpect(model().attribute("results", hasSize(2)));
    }

    @Test
    void noResultPushesInfoAndReturnsToSearch() throws Exception {
        MockHttpSession session = TestSessions.reader();

        mvc.perform(post("/blacklist/searchAction").session(session).param("lastName", "Neexistujúci"))
                .andExpect(redirectedUrl("/blacklist/search"));

        assertThat(TestSessions.popMessages(session))
                .containsExactly(new UiMessage(UiMessage.Level.INFO, "blacklist.search.noResult"));
    }

    @Test
    void newSearchClearsCriteria() throws Exception {
        MockHttpSession session = TestSessions.reader();
        mvc.perform(post("/blacklist/searchAction").session(session).param("lastName", "vzork"));
        assertThat(session.getAttribute("searchCriteria")).isNotNull();

        mvc.perform(get("/blacklist/newSearch").session(session)).andExpect(redirectedUrl("/blacklist/search"));

        assertThat(session.getAttribute("searchCriteria")).isNull();
    }
}
