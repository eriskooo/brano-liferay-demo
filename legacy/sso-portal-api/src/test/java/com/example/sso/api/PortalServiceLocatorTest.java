package com.example.sso.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PortalServiceLocatorTest {

    @AfterEach
    void reset() {
        PortalServiceLocator.setPortalSessionManager(null);
        System.clearProperty(PortalServiceLocator.API_URL_PROPERTY);
        System.clearProperty(PortalServiceLocator.LOGIN_URL_PROPERTY);
    }

    @Test
    @DisplayName("Bez URL API zlyhá so zrozumiteľnou správou")
    void should_failWithClearMessage_when_apiUrlPropertyMissing() {
        assertThatThrownBy(PortalServiceLocator::getPortalSessionManager)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sso.portal.api-url");
    }

    @Test
    @DisplayName("Vráti podstrčenú implementáciu (testy, fake na lokálny beh)")
    void should_returnInjectedManager_when_setExplicitly() {
        PortalSessionManager fake = mock(PortalSessionManager.class);
        PortalServiceLocator.setPortalSessionManager(fake);

        assertThat(PortalServiceLocator.getPortalSessionManager()).isSameAs(fake);
    }

    @Test
    @DisplayName("Podľa system property vytvorí HTTP klienta raz a potom ho opakovane vracia")
    void should_createHttpManagerOnce_when_propertySet() {
        System.setProperty(PortalServiceLocator.API_URL_PROPERTY, "http://sso-mock:8090/sso");

        PortalSessionManager first = PortalServiceLocator.getPortalSessionManager();

        assertThat(first).isInstanceOf(HttpPortalSessionManager.class);
        assertThat(PortalServiceLocator.getPortalSessionManager()).isSameAs(first);
        assertThat(first.getLoginUrl("/x")).isEqualTo("/sso/login?returnUrl=%2Fx");
    }
}
