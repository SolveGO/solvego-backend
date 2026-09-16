package com.kdh.solvego.domain.ai.service;

import com.kdh.solvego.domain.subscription.service.SubscriptionEntitlementService;
import com.kdh.solvego.domain.subscription.type.SubscriptionPlan;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ExplanationUsageLimitPolicy {
    private final SubscriptionEntitlementService entitlementService;
    private final int freeDailyLimit;
    private final int proDailyLimit;

    public ExplanationUsageLimitPolicy(
            SubscriptionEntitlementService entitlementService,
            @Value("${ai.explanation.free-daily-limit}") int freeDailyLimit,
            @Value("${ai.explanation.pro-daily-limit}") int proDailyLimit
    ) {
        if (freeDailyLimit < 1 || proDailyLimit < freeDailyLimit) {
            throw new IllegalArgumentException(
                    "AI explanation daily limits are invalid"
            );
        }
        this.entitlementService = entitlementService;
        this.freeDailyLimit = freeDailyLimit;
        this.proDailyLimit = proDailyLimit;
    }

    public int dailyLimitFor(Long userId) {
        return entitlementService.currentPlan(userId) == SubscriptionPlan.PRO
                ? proDailyLimit
                : freeDailyLimit;
    }
}
