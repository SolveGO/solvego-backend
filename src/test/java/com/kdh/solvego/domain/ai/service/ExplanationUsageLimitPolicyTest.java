package com.kdh.solvego.domain.ai.service;

import com.kdh.solvego.domain.subscription.service.SubscriptionEntitlementService;
import com.kdh.solvego.domain.subscription.type.SubscriptionPlan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExplanationUsageLimitPolicyTest {
    private final SubscriptionEntitlementService entitlementService =
            mock(SubscriptionEntitlementService.class);
    private final ExplanationUsageLimitPolicy policy =
            new ExplanationUsageLimitPolicy(entitlementService, 5, 30);

    @Test
    @DisplayName("FREE 사용자의 일일 해설 한도는 5회다")
    void free_daily_limit_is_five() {
        when(entitlementService.currentPlan(1L)).thenReturn(SubscriptionPlan.FREE);

        assertThat(policy.dailyLimitFor(1L)).isEqualTo(5);
    }

    @Test
    @DisplayName("PRO 사용자의 일일 해설 한도는 30회다")
    void pro_daily_limit_is_thirty() {
        when(entitlementService.currentPlan(1L)).thenReturn(SubscriptionPlan.PRO);

        assertThat(policy.dailyLimitFor(1L)).isEqualTo(30);
    }
}
