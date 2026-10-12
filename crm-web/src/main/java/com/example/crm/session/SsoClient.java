package com.example.crm.session;

import java.time.Duration;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * REST klient SSO portálu. Legacy na to mal „provided“ API JAR + service locator v classloaderi portálu;
 * nová appka volá ten istý endpoint priamo. Krátke timeouty – SSO sa pýta pri každom requeste.
 */
@Component
public class SsoClient {

    private final RestClient rest;

    public SsoClient(RestClient.Builder builder, @Value("${crm.sso.api-url}") String apiUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(1));
        factory.setReadTimeout(Duration.ofSeconds(2));
        this.rest = builder.baseUrl(apiUrl).requestFactory(factory).build();
    }

    /** Identita portálovej session, alebo prázdno, ak ju portál nepozná. */
    public Optional<SsoIdentity> fetch(String ssoSessionId) {
        try {
            SessionView view = rest.get().uri("/api/sessions/{id}", ssoSessionId).retrieve().body(SessionView.class);
            return Optional.ofNullable(view).map(v -> new SsoIdentity(ssoSessionId, v.operator(), v.customer()));
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            }
            throw new SsoUnavailableException("SSO portál vrátil " + e.getStatusCode(), e);
        } catch (RestClientException e) {
            throw new SsoUnavailableException("SSO portál je nedostupný: " + e.getMessage(), e);
        }
    }

    /** Tvar odpovede sso-mocku. */
    record SessionView(OperatorUser operator, CustomerContext customer) {
    }
}
