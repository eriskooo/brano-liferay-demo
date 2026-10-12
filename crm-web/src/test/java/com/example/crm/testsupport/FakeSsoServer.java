package com.example.crm.testsupport;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Fake SSO portálu s rovnakým REST kontraktom ako sso-mock: GET /sso/api/sessions/{id} → 200 JSON / 404. */
public class FakeSsoServer {

    private final Map<String, String> sessions = new ConcurrentHashMap<>();
    private final AtomicInteger calls = new AtomicInteger();
    private volatile int forcedStatus;
    private volatile long delayMillis;
    private HttpServer server;

    public FakeSsoServer start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/sso/api/sessions/", this::handle);
        server.start();
        return this;
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/sso";
    }

    public void put(String id, String operatorLogin, String operatorName, String customerId, String customerName) {
        sessions.put(id, "{\"operator\":{\"login\":\"" + operatorLogin + "\",\"displayName\":\"" + operatorName + "\"},"
                + "\"customer\":{\"customerId\":\"" + customerId + "\",\"displayName\":\"" + customerName + "\"}}");
    }

    public void remove(String id) {
        sessions.remove(id);
    }

    public void forceStatus(int status) {
        this.forcedStatus = status;
    }

    public void delay(long millis) {
        this.delayMillis = millis;
    }

    public int calls() {
        return calls.get();
    }

    public void stop() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        calls.incrementAndGet();
        if (delayMillis > 0) {
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        String path = exchange.getRequestURI().getPath();
        String json = sessions.get(path.substring(path.lastIndexOf('/') + 1));
        if (forcedStatus > 0 || json == null) {
            exchange.sendResponseHeaders(forcedStatus > 0 ? forcedStatus : 404, -1);
            exchange.close();
            return;
        }
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}
