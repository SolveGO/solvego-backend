package com.kdh.solvego.domain.subscription.service;

import com.kdh.solvego.domain.subscription.entity.Subscription;
import com.kdh.solvego.domain.subscription.exception.SubscriptionCheckoutException;
import com.kdh.solvego.domain.subscription.repository.SubscriptionRepository;
import com.kdh.solvego.domain.subscription.type.SubscriptionPlan;
import com.kdh.solvego.domain.user.entity.User;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SubscriptionManagementServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-19T00:00:00Z");

    SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
    SubscriptionManagementService service = new SubscriptionManagementService(
            subscriptions,
            Clock.fixed(NOW, ZoneOffset.UTC)
    );
    Subscription subscription;

    @BeforeEach
    void setup() {
        subscription = Subscription.free(new User("subscriber", "encoded"));
        subscription.initializeCustomerKey("customer");
        subscription.storeBillingKey("ciphertext");
        subscription.activatePaidPro(NOW.minusSeconds(60), NOW.plusSeconds(3600));
        when(subscriptions.findByUserId(1L)).thenReturn(Optional.of(subscription));
        when(subscriptions.findByUserIdForUpdate(1L)).thenReturn(Optional.of(subscription));
    }

    @Test
    void currentResponseContainsBillingState() {
        var response = service.current(1L);

        assertThat(response.plan()).isEqualTo(SubscriptionPlan.PRO);
        assertThat(response.autoRenew()).isTrue();
        assertThat(response.cancelAtPeriodEnd()).isFalse();
        assertThat(response.nextBillingAt()).isEqualTo(NOW.plusSeconds(3600));
    }

    @Test
    void cancellationKeepsCurrentProAndCanBeReactivated() {
        var canceled = service.cancelAtPeriodEnd(1L);

        assertThat(canceled.plan()).isEqualTo(SubscriptionPlan.PRO);
        assertThat(canceled.autoRenew()).isFalse();
        assertThat(canceled.cancelAtPeriodEnd()).isTrue();

        var reactivated = service.reactivate(1L);
        assertThat(reactivated.plan()).isEqualTo(SubscriptionPlan.PRO);
        assertThat(reactivated.autoRenew()).isTrue();
        assertThat(reactivated.cancelAtPeriodEnd()).isFalse();
    }

    @Test
    void expiredSubscriptionCannotChangeAutoRenew() {
        subscription.activatePaidPro(NOW.minusSeconds(3600), NOW);

        assertThatThrownBy(() -> service.cancelAtPeriodEnd(1L))
                .isInstanceOf(SubscriptionCheckoutException.class);
        assertThatThrownBy(() -> service.reactivate(1L))
                .isInstanceOf(SubscriptionCheckoutException.class);
    }
}
