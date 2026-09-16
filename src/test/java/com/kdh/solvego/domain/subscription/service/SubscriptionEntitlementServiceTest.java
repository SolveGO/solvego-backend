package com.kdh.solvego.domain.subscription.service;

import com.kdh.solvego.domain.subscription.entity.Subscription;
import com.kdh.solvego.domain.subscription.repository.SubscriptionRepository;
import com.kdh.solvego.domain.subscription.type.SubscriptionPlan;
import com.kdh.solvego.domain.user.entity.User;
import com.kdh.solvego.domain.user.exception.UserNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SubscriptionEntitlementServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-16T00:00:00Z");

    private final SubscriptionRepository subscriptionRepository =
            mock(SubscriptionRepository.class);
    private final SubscriptionEntitlementService service =
            new SubscriptionEntitlementService(
                    subscriptionRepository,
                    Clock.fixed(NOW, ZoneOffset.UTC)
            );

    @Test
    @DisplayName("FREE 구독은 FREE 권한을 반환한다")
    void free_subscription_has_free_entitlement() {
        Subscription subscription = Subscription.free(
                new User("free-user", "encoded")
        );
        when(subscriptionRepository.findByUserId(1L))
                .thenReturn(Optional.of(subscription));

        assertThat(service.currentPlan(1L)).isEqualTo(SubscriptionPlan.FREE);
    }

    @Test
    @DisplayName("현재 기간이 유효한 ACTIVE PRO는 PRO 권한을 반환한다")
    void active_pro_subscription_has_pro_entitlement() {
        Subscription subscription = Subscription.free(
                new User("pro-user", "encoded")
        );
        subscription.activatePro(NOW.minusSeconds(60), NOW.plusSeconds(3600));
        when(subscriptionRepository.findByUserId(1L))
                .thenReturn(Optional.of(subscription));

        assertThat(service.currentPlan(1L)).isEqualTo(SubscriptionPlan.PRO);
    }

    @Test
    @DisplayName("기간이 만료된 ACTIVE PRO는 FREE 권한을 반환한다")
    void expired_pro_subscription_has_free_entitlement() {
        Subscription subscription = Subscription.free(
                new User("expired-user", "encoded")
        );
        subscription.activatePro(NOW.minusSeconds(3600), NOW);
        when(subscriptionRepository.findByUserId(1L))
                .thenReturn(Optional.of(subscription));

        assertThat(service.currentPlan(1L)).isEqualTo(SubscriptionPlan.FREE);
    }

    @Test
    @DisplayName("구독 레코드가 없으면 사용자를 찾을 수 없는 것으로 처리한다")
    void missing_subscription_is_rejected() {
        when(subscriptionRepository.findByUserId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.currentPlan(1L))
                .isInstanceOf(UserNotFoundException.class);
    }
}
