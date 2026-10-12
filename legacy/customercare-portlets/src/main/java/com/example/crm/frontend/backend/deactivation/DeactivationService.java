package com.example.crm.frontend.backend.deactivation;

import java.time.LocalDate;
import java.util.Optional;

/** Port na backend žiadostí o zrušenie (v legacy manager + viacero backend služieb). */
public interface DeactivationService {

    /** Zaeviduje žiadosť; príliš skorý dátum účinnosti → {@link IllegalArgumentException}. */
    DeactivationRequest submit(CustomerProduct product, DeactivationType type, LocalDate noticeDate,
                               LocalDate effectiveDate, String operator);

    /** Žiadosť podľa ID. */
    Optional<DeactivationRequest> find(long id);
}
