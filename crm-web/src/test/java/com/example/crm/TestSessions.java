package com.example.crm;

import com.example.crm.session.CrmSessions;
import com.example.crm.session.CustomerContext;
import com.example.crm.session.OperatorUser;
import com.example.crm.session.SharedSessionKeys;
import com.example.crm.session.SsoIdentity;
import com.example.crm.session.UiMessage;
import com.example.crm.testsupport.DemoIdentities;
import com.example.crm.testsupport.TestInfrastructure;
import java.util.List;
import java.util.UUID;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import tools.jackson.databind.json.JsonMapper;

/** HTTP session „po SSO prihlásení“ pre MockMvc testy (identita podstrčená, zdieľaná session v pamäti). */
public final class TestSessions {

    private TestSessions() {
    }

    public static MockHttpSession loggedIn(OperatorUser operator, CustomerContext customer) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(TestInfrastructure.TEST_IDENTITY,
                new SsoIdentity("test-" + UUID.randomUUID(), operator, customer));
        return session;
    }

    public static MockHttpSession admin() {
        return loggedIn(DemoIdentities.ADMIN, DemoIdentities.PERSON);
    }

    public static MockHttpSession reader() {
        return loggedIn(DemoIdentities.READER, DemoIdentities.PERSON);
    }

    /** Hlášky čakajúce v zdieľanej session operátora (spotrebuje ich). */
    public static List<UiMessage> popMessages(MockHttpSession session) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        CrmSessions sessions = new CrmSessions(new TestInfrastructure.SessionAttributeIdentityResolver(),
                TestInfrastructure.STORE, new SharedSessionKeys(JsonMapper.builder().build()));
        return sessions.of(request).popMessages();
    }
}
