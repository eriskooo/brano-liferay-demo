package com.example.crm.frontend;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.crm.frontend.base.i18n.MessageSourceCustom;
import com.example.crm.frontend.base.session.SessionManagerCustom;
import com.example.crm.frontend.base.session.SharedKeys;
import com.example.crm.frontend.base.web.PortletAccessGuard;
import com.example.crm.frontend.base.web.SimpleMappingExceptionResolverCustom;
import com.example.sso.api.PortalServiceLocator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.FileSystemResourceLoader;
import org.springframework.mock.web.MockServletContext;
import org.springframework.mock.web.portlet.MockPortletConfig;
import org.springframework.mock.web.portlet.MockPortletContext;
import org.springframework.web.context.support.XmlWebApplicationContext;
import org.springframework.web.portlet.context.XmlPortletApplicationContext;

/**
 * Načíta skutočné XML kontexty WAR-u (root + každý portlet) bez portálu. Preklep v XML alebo nevyriešiteľný
 * {@code @Autowired} v controlleri sa tak ukáže pri {@code mvn verify}, nie až v logu Liferay po deployi.
 */
class PortletContextWiringTest {

    private static final String WEBAPP = "src/main/webapp";

    @BeforeAll
    static void systemProperties() {
        System.setProperty(PortalServiceLocator.API_URL_PROPERTY, "http://127.0.0.1:1/sso");
    }

    @AfterAll
    static void cleanUp() {
        System.clearProperty(PortalServiceLocator.API_URL_PROPERTY);
        PortalServiceLocator.setPortalSessionManager(null);
    }

    @Test
    @DisplayName("Root kontext obsahuje fasádu, strážcu, i18n a register zdieľaných kľúčov")
    void should_wireRootBeans_when_applicationContextLoaded() {
        try (XmlWebApplicationContext root = root()) {
            assertThat(root.getBean(SessionManagerCustom.class)).isNotNull();
            assertThat(root.getBean(PortletAccessGuard.class)).isNotNull();
            assertThat(root.getBean("messageSource")).isInstanceOf(MessageSourceCustom.class);
            SharedKeys keys = root.getBean(SharedKeys.class);
            assertThat(keys.isShared("APP_BLACKLIST_LAST_CRITERIA")).isTrue();
            assertThat(keys.isShared("APP_LAST_VIEWED_RECORD")).isTrue();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"blacklist", "dashboard", "header"})
    @DisplayName("Kontext každého portletu sa načíta nad root kontextom a má exception resolver")
    void should_loadPortletContext_when_definedInPortletXml(String portlet) {
        try (XmlWebApplicationContext root = root()) {
            MockPortletContext portletContext = new MockPortletContext(WEBAPP, new FileSystemResourceLoader());
            XmlPortletApplicationContext context = new XmlPortletApplicationContext();
            context.setPortletContext(portletContext);
            context.setPortletConfig(new MockPortletConfig(portletContext, portlet));
            context.setParent(root);
            context.setConfigLocation("/WEB-INF/" + portlet + "-portlet.xml");
            context.refresh();

            assertThat(context.getBean(SimpleMappingExceptionResolverCustom.class)).isNotNull();
            context.close();
        }
    }

    private static XmlWebApplicationContext root() {
        XmlWebApplicationContext root = new XmlWebApplicationContext();
        root.setServletContext(new MockServletContext(WEBAPP, new FileSystemResourceLoader()));
        root.setConfigLocation("/WEB-INF/applicationContext.xml");
        root.refresh();
        return root;
    }
}
