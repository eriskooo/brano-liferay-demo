package com.example.crm.frontend.backend.deactivation;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Zaevidovaná žiadosť o zrušenie. */
public final class DeactivationRequest {

    private final long id;
    private final String productId;
    private final String customerId;
    private final String productName;
    private final DeactivationType type;
    private final LocalDate noticeDate;
    private final LocalDate effectiveDate;
    private final String operator;
    private final LocalDateTime createdAt;

    public DeactivationRequest(long id, String productId, String customerId, String productName, DeactivationType type,
                               LocalDate noticeDate, LocalDate effectiveDate, String operator, LocalDateTime createdAt) {
        this.id = id;
        this.productId = productId;
        this.customerId = customerId;
        this.productName = productName;
        this.type = type;
        this.noticeDate = noticeDate;
        this.effectiveDate = effectiveDate;
        this.operator = operator;
        this.createdAt = createdAt;
    }

    public long getId() {
        return id;
    }

    public String getProductId() {
        return productId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getProductName() {
        return productName;
    }

    public DeactivationType getType() {
        return type;
    }

    public LocalDate getNoticeDate() {
        return noticeDate;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }

    public String getOperator() {
        return operator;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
