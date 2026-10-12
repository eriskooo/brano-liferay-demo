package com.example.crm.frontend.base.session;

import static org.assertj.core.api.Assertions.assertThat;

import javax.portlet.PortletSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.portlet.MockPortletSession;

class PortletSessionStoreTest {

    @Test
    @DisplayName("Zapisuje do APPLICATION_SCOPE (vidia to všetky portlety vo WAR-e)")
    void should_writeToApplicationScope_when_put() {
        MockPortletSession session = new MockPortletSession();
        PortletSessionStore store = new PortletSessionStore(new FakeRequestAwareManager(session));

        store.put("APP_X", "hodnota");

        assertThat(session.getAttribute("APP_X", PortletSession.APPLICATION_SCOPE)).isEqualTo("hodnota");
        assertThat(session.getAttribute("APP_X", PortletSession.PORTLET_SCOPE)).isNull();
        assertThat(store.get("APP_X")).isEqualTo("hodnota");
    }

    @Test
    @DisplayName("Čítanie nevytvorí session")
    void should_notCreateSession_when_reading() {
        FakeRequestAwareManager request = new FakeRequestAwareManager(null);

        assertThat(new PortletSessionStore(request).get("APP_X")).isNull();
        assertThat(request.getPortletRequest().getPortletSession(false)).isNull();
    }

    @Test
    @DisplayName("clear() zmaže len kľúče fasády (APP_), ostatné atribúty nechá")
    void should_removeOnlyPrefixedKeys_when_cleared() {
        MockPortletSession session = new MockPortletSession();
        session.setAttribute("APP_A", "1", PortletSession.APPLICATION_SCOPE);
        session.setAttribute("APP_B", "2", PortletSession.APPLICATION_SCOPE);
        session.setAttribute("OTHER", "3", PortletSession.APPLICATION_SCOPE);

        new PortletSessionStore(new FakeRequestAwareManager(session)).clear();

        assertThat(session.getAttribute("APP_A", PortletSession.APPLICATION_SCOPE)).isNull();
        assertThat(session.getAttribute("APP_B", PortletSession.APPLICATION_SCOPE)).isNull();
        assertThat(session.getAttribute("OTHER", PortletSession.APPLICATION_SCOPE)).isEqualTo("3");
    }

    @Test
    @DisplayName("remove() odstráni kľúč")
    void should_removeKey_when_removed() {
        MockPortletSession session = new MockPortletSession();
        PortletSessionStore store = new PortletSessionStore(new FakeRequestAwareManager(session));
        store.put("APP_X", "1");

        store.remove("APP_X");

        assertThat(store.get("APP_X")).isNull();
    }
}
