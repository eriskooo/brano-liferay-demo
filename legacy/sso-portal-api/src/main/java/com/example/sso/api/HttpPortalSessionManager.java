package com.example.sso.api;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import javax.servlet.http.HttpServletRequest;

/**
 * Implementácia API nad REST rozhraním SSO mocku. Java 8 bez knižníc navyše ({@link HttpURLConnection}),
 * lebo JAR beží v spoločnom classloaderi portálu. Krátke timeouty: SSO sa volá pri každom requeste.
 */
public class HttpPortalSessionManager implements PortalSessionManager {

    private final String apiUrl;
    private final String loginUrl;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /** Vytvorí klienta pre API na {@code apiUrl} (napr. {@code http://sso-mock:8090/sso}). */
    public HttpPortalSessionManager(String apiUrl, String loginUrl, int connectTimeoutMs, int readTimeoutMs) {
        this.apiUrl = apiUrl;
        this.loginUrl = loginUrl;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
    }

    @Override
    public PortalSession getPortalSession(HttpServletRequest request) {
        String sessionId = SsoCookies.readSessionId(request);
        if (sessionId == null) {
            return null;
        }
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(apiUrl + "/api/sessions/" + sessionId).openConnection();
            connection.setConnectTimeout(connectTimeoutMs);
            connection.setReadTimeout(readTimeoutMs);
            connection.setRequestProperty("Accept", "application/json");
            int status = connection.getResponseCode();
            if (status == HttpURLConnection.HTTP_NOT_FOUND) {
                return null;
            }
            if (status != HttpURLConnection.HTTP_OK) {
                throw new SsoUnavailableException("SSO portál vrátil HTTP " + status, null);
            }
            try (InputStream body = connection.getInputStream()) {
                PortalSession session = mapper.readValue(body, PortalSession.class);
                session.setSessionId(sessionId);
                return session;
            }
        } catch (IOException e) {
            throw new SsoUnavailableException("SSO portál je nedostupný: " + e.getMessage(), e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    @Override
    public String getLoginUrl(String returnUrl) {
        try {
            return loginUrl + "?returnUrl=" + URLEncoder.encode(returnUrl, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
