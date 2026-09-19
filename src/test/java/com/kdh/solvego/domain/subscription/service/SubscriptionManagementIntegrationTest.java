package com.kdh.solvego.domain.subscription.service;

import com.kdh.solvego.domain.subscription.billing.BillingKeyCipher;
import com.kdh.solvego.domain.subscription.billing.RenewalTransactions;
import com.kdh.solvego.domain.subscription.entity.Subscription;
import com.kdh.solvego.domain.subscription.repository.SubscriptionRepository;
import com.kdh.solvego.domain.subscription.type.SubscriptionPlan;
import com.kdh.solvego.domain.user.entity.User;
import com.kdh.solvego.domain.user.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "payment.renewal.scheduler-enabled=false")
@ActiveProfiles("test")
class SubscriptionManagementIntegrationTest {
    private static final String ENCRYPTION_KEY = java.util.Base64.getEncoder()
            .encodeToString(new byte[32]);

    @DynamicPropertySource
    static void encryptionKey(DynamicPropertyRegistry registry) {
        registry.add("payment.billing-encryption-key", () -> ENCRYPTION_KEY);
    }

    @Autowired SubscriptionManagementService service;
    @Autowired RenewalTransactions renewalTransactions;
    @Autowired BillingKeyCipher cipher;
    @Autowired UserRepository users;
    @Autowired SubscriptionRepository subscriptions;

    Long userId;
    Long subscriptionId;

    @AfterEach
    void cleanup() {
        if (userId != null) users.deleteById(userId);
    }

    @Test
    void canceledSubscriptionKeepsProAndIsExcludedUntilReactivated() {
        Instant now = Instant.now();
        Instant periodEnd = now.plus(1, ChronoUnit.DAYS);
        User user = users.save(new User("manage-" + UUID.randomUUID(), "encoded"));
        userId = user.getId();
        Subscription subscription = Subscription.free(user);
        subscription.initializeCustomerKey("customer-" + UUID.randomUUID());
        subscription.storeBillingKey(cipher.encrypt("billing-key", subscription.getCustomerKey()));
        subscription.activatePaidPro(now.minus(1, ChronoUnit.DAYS), periodEnd);
        subscriptionId = subscriptions.saveAndFlush(subscription).getId();

        var canceled = service.cancelAtPeriodEnd(userId);

        assertThat(canceled.plan()).isEqualTo(SubscriptionPlan.PRO);
        assertThat(canceled.currentPeriodEndAt()).isEqualTo(periodEnd);
        assertThat(canceled.autoRenew()).isFalse();
        assertThat(canceled.cancelAtPeriodEnd()).isTrue();
        assertThat(renewalTransactions.findDueCandidateIds(periodEnd.plusSeconds(1), 100))
                .doesNotContain(subscriptionId);

        var reactivated = service.reactivate(userId);

        assertThat(reactivated.autoRenew()).isTrue();
        assertThat(reactivated.cancelAtPeriodEnd()).isFalse();
        assertThat(renewalTransactions.findDueCandidateIds(periodEnd.plusSeconds(1), 100))
                .contains(subscriptionId);
    }
}
