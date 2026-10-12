package com.example.crm.frontend.dashboard;

import com.example.crm.frontend.backend.deactivation.DeactivationType;
import java.io.Serializable;
import java.time.LocalDate;

/** Formulár žiadosti o zrušenie; v legacy držaný v {@code @SessionAttributes} (viď pasca v DashboardController). */
public class DeactivationForm implements Serializable {

    private static final long serialVersionUID = 1L;

    private String productId;
    private DeactivationType type;
    private LocalDate noticeDate;
    private LocalDate effectiveDate;

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public DeactivationType getType() {
        return type;
    }

    public void setType(DeactivationType type) {
        this.type = type;
    }

    public LocalDate getNoticeDate() {
        return noticeDate;
    }

    public void setNoticeDate(LocalDate noticeDate) {
        this.noticeDate = noticeDate;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }

    public void setEffectiveDate(LocalDate effectiveDate) {
        this.effectiveDate = effectiveDate;
    }
}
