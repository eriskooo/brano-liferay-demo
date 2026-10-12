package com.example.crm.frontend.backend.deactivation;

import java.time.LocalDate;

/**
 * DEMO pravidlá – VYMYSLENÉ, nie business pravidlá klienta. Žijú na jednom mieste a používa ich validátor
 * formulára, JSON resource pre AJAX aj backend služba.
 */
public final class DeactivationRules {

    private DeactivationRules() {
    }

    /** Najskorší dátum účinnosti pre typ žiadosti a dátum podania. */
    public static LocalDate earliestEffectiveDate(DeactivationType type, LocalDate noticeDate) {
        switch (type) {
            case TRANSFER_TO_CUSTOMER:
                return noticeDate;
            case CUSTOMER_DEACTIVATION:
                return noticeDate.plusDays(30);
            default:
                return noticeDate.plusDays(1);
        }
    }
}
