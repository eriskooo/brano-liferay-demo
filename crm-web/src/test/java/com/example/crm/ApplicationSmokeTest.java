package com.example.crm;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApplicationSmokeTest {

    @LocalServerPort
    int port;

    @Test
    @DisplayName("Statické súbory (náhrada theme) sú pod kontextom /crm")
    void should_serveStaticAssets_when_requestedUnderContextPath() throws Exception {
        assertThat(get("/crm/css/theme.css")).contains(".crm-wrapper");
        assertThat(get("/crm/css/portlet.css")).contains(".crm-portlet label");
        assertThat(get("/crm/css/crm.css")).isNotNull();
        assertThat(get("/crm/js/crm.js")).contains("X-Requested-With");
    }

    private String get(String path) throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return response.body();
    }
}
