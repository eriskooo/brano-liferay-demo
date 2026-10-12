package com.example.ssomock;

import com.example.ssomock.Directory.Customer;
import com.example.ssomock.Directory.Operator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** Session portálu v pamäti procesu. Reálny portál ju drží vo svojom úložisku; pre demo stačí mapa. */
@Component
public class PortalSessionStore {

    public record PortalSession(String id, Operator operator, Customer customer) {
    }

    private final Map<String, PortalSession> sessions = new ConcurrentHashMap<>();

    /** Vytvorí session a vráti jej ID (UUID – zodpovedá formátu, ktorý akceptuje CRM). */
    public String create(Operator operator, Customer customer) {
        String id = UUID.randomUUID().toString();
        sessions.put(id, new PortalSession(id, operator, customer));
        return id;
    }

    /** Session podľa ID. */
    public Optional<PortalSession> find(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(sessions.get(id));
    }

    /** Zmení zákazníka; vráti {@code false}, ak session neexistuje. */
    public boolean changeCustomer(String id, Customer customer) {
        return sessions.computeIfPresent(id, (key, s) -> new PortalSession(key, s.operator(), customer)) != null;
    }

    /** Zruší session. */
    public void remove(String id) {
        if (id != null) {
            sessions.remove(id);
        }
    }
}
