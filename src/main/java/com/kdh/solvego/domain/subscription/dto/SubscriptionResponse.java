package com.kdh.solvego.domain.subscription.dto;

import com.kdh.solvego.domain.subscription.entity.Subscription;
import com.kdh.solvego.domain.subscription.type.SubscriptionPlan;
import com.kdh.solvego.domain.subscription.type.SubscriptionStatus;
import java.time.Instant;

public record SubscriptionResponse(
        SubscriptionPlan plan,
        SubscriptionStatus status,
        Instant currentPeriodStartAt,
        Instant currentPeriodEndAt,
        Instant nextBillingAt,
        boolean autoRenew,
        boolean cancelAtPeriodEnd
) {
    public static SubscriptionResponse from(Subscription subscription, Instant now) {
        return new SubscriptionResponse(
                subscription.effectivePlan(now),
                subscription.getStatus(),
                subscription.getCurrentPeriodStartAt(),
                subscription.getCurrentPeriodEndAt(),
                subscription.getNextBillingAt(),
                subscription.isAutoRenew(),
                subscription.isCancelAtPeriodEnd()
        );
    }
}
