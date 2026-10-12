package com.example.crm.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.crm.testsupport.FakeSsoServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.HttpCookie;
import java.net.http.HttpResponse;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;

/**
 * Nová appka so skutočným Redisom a SSO kontraktom: overuje presne to, na čom stojí súbeh so starou appkou –
 * kľúče a JSON v hashi crm:session:{SSO_ID}, hlášky naprieč appkami a vlastnú session cookie.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "crm.test.fake-infrastructure=false")
class SharedSessionFlowTest {

    private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);
    private static final FakeSsoServer SSO = new FakeSsoServer();

    static {
        REDIS.start();
        try {
            SSO.start();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        SSO.put("it-admin", "spravca", "Správca", "C-1001", "Ján Vzorový");
        SSO.put("it-reader", "citatel", "Čitateľ", "C-1001", "Ján Vzorový");
    }

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("crm.sso.api-url", SSO::baseUrl);
    }

    @AfterAll
    static void stop() {
        SSO.stop();
        REDIS.stop();
    }

    @LocalServerPort
    int port;

    @Autowired
    StringRedisTemplate redis;

    @Test
    @DisplayName("Bez SSO cookie presmeruje na SSO login portálu")
    void should_redirectToSsoLogin_when_noSsoCookie() throws Exception {
        HttpResponse<String> response = new WebClient(port).get("/crm/blacklist/search");

        assertThat(response.uri().getPath()).isEqualTo("/sso/login");
        assertThat(response.uri().getQuery()).isEqualTo("returnUrl=/crm/blacklist/search");
    }

    @Test
    @DisplayName("Detail zapíše posledný záznam do Redisu v JSON tvare, ktorý číta stará appka")
    void should_shareLastViewedWithLegacy_when_detailOpened() throws Exception {
        WebClient client = new WebClient(port).addCookie("SSO_ID", "it-admin");

        HttpResponse<String> detail = client.get("/crm/blacklist/detail?detailId=1");

        assertThat(detail.statusCode()).isEqualTo(200);
        assertThat(detail.body()).contains("Peter Vzorka").contains("Správca").contains("SSO_ID = it-admin");
        assertThat(redis.opsForHash().get("crm:session:it-admin", "APP_LAST_VIEWED_RECORD"))
                .isEqualTo("{\"id\":1,\"fullName\":\"Peter Vzorka\"}");
        assertThat(redis.getExpire("crm:session:it-admin")).isPositive();
        WebClient.assertNoMissingMessages(detail.body());
    }

    @Test
    @DisplayName("Vyhľadávanie s diakritikou: výsledok, kritériá v Redise, vlastná session cookie /crm")
    void should_searchAndShareCriteria_when_diacriticsPosted() throws Exception {
        WebClient client = new WebClient(port).addCookie("SSO_ID", "it-admin");

        HttpResponse<String> result = client.post("/crm/blacklist/searchAction", Map.of("firstName", "Mária"));

        assertThat(result.body()).contains("Mária Príkladová");
        assertThat((String) redis.opsForHash().get("crm:session:it-admin", "APP_BLACKLIST_LAST_CRITERIA"))
                .contains("\"firstName\":\"Mária\"");
    }

    @Test
    @DisplayName("Session cookie novej appky je CRM_SESSION s Path=/crm, JSESSIONID nevzniká")
    void should_useOwnSessionCookie_when_sessionCreated() throws Exception {
        WebClient client = new WebClient(port).addCookie("SSO_ID", "it-admin");

        client.post("/crm/blacklist/searchAction", Map.of("lastName", "Vzork"));

        assertThat(client.cookies().getCookieStore().getCookies())
                .extracting(HttpCookie::getName).contains("CRM_SESSION").doesNotContain("JSESSIONID");
        assertThat(client.cookies().getCookieStore().getCookies())
                .filteredOn(c -> c.getName().equals("CRM_SESSION")).extracting(HttpCookie::getPath).containsExactly("/crm");
    }

    @Test
    @DisplayName("Hláška zapísaná starou appkou do Redisu sa zobrazí raz a z hashu zmizne")
    void should_showLegacyMessageOnce_when_pageRendered() throws Exception {
        redis.opsForHash().put("crm:session:it-admin", "APP_MESSAGES",
                "[{\"level\":\"INFO\",\"key\":\"dashboard.deactivation.submitted\"}]");
        WebClient client = new WebClient(port).addCookie("SSO_ID", "it-admin");

        assertThat(client.get("/crm/blacklist/search").body()).contains("Žiadosť o zrušenie bola prijatá.");
        assertThat(redis.opsForHash().hasKey("crm:session:it-admin", "APP_MESSAGES")).isFalse();
        assertThat(client.get("/crm/blacklist/search").body()).doesNotContain("Žiadosť o zrušenie bola prijatá.");
    }

    @Test
    @DisplayName("Zmena stavu: hláška po PRG raz; čitateľ dostane 403; AJAX bez SSO 401; GET na mutáciu 405")
    void should_enforceAclAndMethods_when_modifying() throws Exception {
        WebClient admin = new WebClient(port).addCookie("SSO_ID", "it-admin");
        HttpResponse<String> changed = admin.post("/crm/blacklist/changeAction?detailId=7",
                Map.of("state", "INACTIVE", "validTo", ""));
        assertThat(changed.body()).contains("Záznam bol zmenený.");

        WebClient reader = new WebClient(port).addCookie("SSO_ID", "it-reader");
        assertThat(reader.post("/crm/blacklist/changeAction?detailId=7", Map.of("state", "ACTIVE", "validTo", ""))
                .statusCode()).isEqualTo(403);

        assertThat(new WebClient(port).get("/crm/blacklist/resource/refreshCreateConditionTypes?stateReason=DEBT",
                WebClient.AJAX).statusCode()).isEqualTo(401);
        assertThat(admin.get("/crm/blacklist/resource/removeIdentAction?detailId=1&identId=2").statusCode())
                .isEqualTo(405);
    }
}
