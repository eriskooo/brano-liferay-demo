package com.example.crm.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

/** Prehliadač „bez JavaScriptu“ pre testy s reálnym Tomcatom: cookies + nasledovanie redirectov v rámci appky. */
public final class WebClient {

    public static final String[] AJAX = {"X-Requested-With", "XMLHttpRequest"};

    private final CookieManager cookies = new CookieManager();
    private final HttpClient http;
    private final String baseUrl;

    public WebClient(int port) {
        this.baseUrl = "http://localhost:" + port;
        this.http = HttpClient.newBuilder().cookieHandler(cookies).followRedirects(HttpClient.Redirect.NORMAL).build();
    }

    /** Cookie pre celý host (napr. SSO_ID, ktorú by nastavil SSO portál). */
    public WebClient addCookie(String name, String value) {
        HttpCookie cookie = new HttpCookie(name, value);
        cookie.setPath("/");
        // Verzia 0 (Netscape) ako v prehliadači – verzia 1 by hodnotu poslala v úvodzovkách ("it-admin").
        cookie.setVersion(0);
        cookies.getCookieStore().add(URI.create(baseUrl), cookie);
        return this;
    }

    public CookieManager cookies() {
        return cookies;
    }

    public HttpResponse<String> get(String path, String... headers) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path)).GET();
        if (headers.length > 0) {
            request.headers(headers);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    public HttpResponse<String> post(String path, Map<String, String> form, String... headers) throws Exception {
        String body = form.entrySet().stream()
                .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .collect(Collectors.joining("&"));
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (headers.length > 0) {
            request.headers(headers);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    /** Každý chýbajúci i18n kľúč sa vykreslí ako ??kľúč?? – stránka nesmie obsahovať ani jeden. */
    public static void assertNoMissingMessages(String body) {
        assertThat(body).doesNotContain("??");
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
