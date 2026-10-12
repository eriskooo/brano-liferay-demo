package com.example.crm.frontend.backend.deactivation;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** In-memory evidencia žiadostí. */
public class InMemoryDeactivationService implements DeactivationService {

    private final Clock clock;
    private final Map<Long, DeactivationRequest> requests = new ConcurrentHashMap<>();
    private final AtomicLong ids = new AtomicLong();

    public InMemoryDeactivationService(Clock clock) {
        this.clock = clock;
    }

    @Override
    public DeactivationRequest submit(CustomerProduct product, DeactivationType type, LocalDate noticeDate,
                                      LocalDate effectiveDate, String operator) {
        LocalDate earliest = DeactivationRules.earliestEffectiveDate(type, noticeDate);
        // Backend overí pravidlo znova – validácia formulára je pre používateľa, nie ochrana dát.
        if (effectiveDate.isBefore(earliest)) {
            throw new IllegalArgumentException("Dátum účinnosti " + effectiveDate + " je pred " + earliest);
        }
        DeactivationRequest request = new DeactivationRequest(ids.incrementAndGet(), product.getId(),
                product.getCustomerId(), product.getName(), type, noticeDate, effectiveDate, operator,
                LocalDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES));
        requests.put(request.getId(), request);
        return request;
    }

    @Override
    public Optional<DeactivationRequest> find(long id) {
        return Optional.ofNullable(requests.get(id));
    }
}
