package com.kdh.solvego.domain.subscription.service;

import com.kdh.solvego.domain.subscription.repository.SubscriptionRepository;
import com.kdh.solvego.domain.subscription.type.SubscriptionPlan;
import com.kdh.solvego.domain.user.exception.UserNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

@Service
public class SubscriptionEntitlementService {
    private final SubscriptionRepository subscriptionRepository;
    private final Clock clock;

    @Autowired
    public SubscriptionEntitlementService(
            SubscriptionRepository subscriptionRepository
    ) {
        this(subscriptionRepository, Clock.systemUTC());
    }

    SubscriptionEntitlementService(
            SubscriptionRepository subscriptionRepository,
            Clock clock
    ) {
        this.subscriptionRepository = subscriptionRepository;
        this.clock = clock;
    }

    public SubscriptionPlan currentPlan(Long userId) {
        return subscriptionRepository.findByUserId(userId)
                .orElseThrow(UserNotFoundException::new)
                .effectivePlan(Instant.now(clock));
    }
}
