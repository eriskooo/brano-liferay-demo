package com.example.ssomock;

import com.example.ssomock.Directory.Customer;
import com.example.ssomock.Directory.Operator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** REST API, ktoré volá CRM (stará appka cez {@code sso-portal-api}, nová v podprojekte 2 priamo). */
@RestController
public class ApiController {

    /** Tvar odpovede – kontrakt s {@code com.example.sso.api.PortalSession}. */
    public record SessionView(Operator operator, Customer customer) {
    }

    private final PortalSessionStore sessions;

    public ApiController(PortalSessionStore sessions) {
        this.sessions = sessions;
    }

    /** Operátor a zákazník session, alebo 404. */
    @GetMapping("/api/sessions/{id}")
    public ResponseEntity<SessionView> session(@PathVariable String id) {
        return sessions.find(id)
                .map(s -> ResponseEntity.ok(new SessionView(s.operator(), s.customer())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
