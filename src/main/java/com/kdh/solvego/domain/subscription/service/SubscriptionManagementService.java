package com.kdh.solvego.domain.subscription.service;

import com.kdh.solvego.domain.subscription.dto.SubscriptionResponse;
import com.kdh.solvego.domain.subscription.entity.Subscription;
import com.kdh.solvego.domain.subscription.exception.SubscriptionCheckoutException;
import com.kdh.solvego.domain.subscription.repository.SubscriptionRepository;
import com.kdh.solvego.domain.subscription.type.SubscriptionPlan;
import com.kdh.solvego.domain.user.exception.UserNotFoundException;
import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubscriptionManagementService {
    private final SubscriptionRepository subscriptions;
    private final Clock clock;

    @Autowired
    public SubscriptionManagementService(SubscriptionRepository subscriptions) {
        this(subscriptions, Clock.systemUTC());
    }

    SubscriptionManagementService(SubscriptionRepository subscriptions, Clock clock) {
        this.subscriptions = subscriptions;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public SubscriptionResponse current(Long userId) {
        Instant now = Instant.now(clock);
        return SubscriptionResponse.from(find(userId), now);
    }

    @Transactional
    public SubscriptionResponse cancelAtPeriodEnd(Long userId) {
        Instant now = Instant.now(clock);
        Subscription subscription = lock(userId);
        requireCurrentPro(subscription, now);
        subscription.cancelAutoRenewAtPeriodEnd();
        return SubscriptionResponse.from(subscription, now);
    }

    @Transactional
    public SubscriptionResponse reactivate(Long userId) {
        Instant now = Instant.now(clock);
        Subscription subscription = lock(userId);
        requireCurrentPro(subscription, now);
        if (!subscription.canReactivateAutoRenew(now)) {
            throw new SubscriptionCheckoutException("자동결제를 다시 활성화할 수 없습니다.");
        }
        subscription.reactivateAutoRenew();
        return SubscriptionResponse.from(subscription, now);
    }

    private Subscription find(Long userId) {
        return subscriptions.findByUserId(userId).orElseThrow(UserNotFoundException::new);
    }

    private Subscription lock(Long userId) {
        return subscriptions.findByUserIdForUpdate(userId).orElseThrow(UserNotFoundException::new);
    }

    private void requireCurrentPro(Subscription subscription, Instant now) {
        if (subscription.effectivePlan(now) != SubscriptionPlan.PRO) {
            throw new SubscriptionCheckoutException("현재 이용 중인 PRO 구독이 없습니다.");
        }
    }
}
