package com.example.crm.frontend.backend.blacklist;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ConditionTypeTest {

    @Test
    @DisplayName("Povolené podmienky závisia od dôvodu, null = všetky")
    void should_dependOnReason_when_listingAllowedConditionTypes() {
        assertThat(ConditionType.allowedFor(StateReason.FRAUD)).containsExactly(ConditionType.BLOCK_ALL);
        assertThat(ConditionType.allowedFor(StateReason.DEBT))
                .containsExactly(ConditionType.REQUIRE_DEPOSIT, ConditionType.MANUAL_APPROVAL);
        assertThat(ConditionType.allowedFor(StateReason.ABUSE))
                .containsExactly(ConditionType.MANUAL_APPROVAL, ConditionType.BLOCK_ALL);
        assertThat(ConditionType.allowedFor(null)).containsExactly(ConditionType.values());
    }
}
