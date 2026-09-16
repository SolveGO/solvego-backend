package com.kdh.solvego.domain.ai.service;

import com.kdh.solvego.domain.user.entity.User;
import com.kdh.solvego.domain.user.repository.UserRepository;
import com.kdh.solvego.domain.user.type.SubscriptionPlan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExplanationUsageLimitPolicyTest {
    private static final Instant NOW = Instant.parse("2026-09-16T00:00:00Z");

    private final UserRepository userRepository = mock(UserRepository.class);
    private final ExplanationUsageLimitPolicy policy =
            new ExplanationUsageLimitPolicy(
                    userRepository,
                    5,
                    30,
                    Clock.fixed(NOW, ZoneOffset.UTC)
            );

    @Test
    @DisplayName("FREE 사용자의 일일 해설 한도는 5회다")
    void free_daily_limit_is_five() {
        User user = new User("free-user", "encoded");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThat(policy.dailyLimitFor(1L)).isEqualTo(5);
    }

    @Test
    @DisplayName("활성 PRO 사용자의 일일 해설 한도는 30회다")
    void active_pro_daily_limit_is_thirty() {
        User user = new User("pro-user", "encoded");
        user.activateSubscription(
                SubscriptionPlan.PRO,
                NOW.minusSeconds(60),
                NOW.plusSeconds(3600)
        );
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThat(policy.dailyLimitFor(1L)).isEqualTo(30);
    }

    @Test
    @DisplayName("만료된 PRO 사용자는 FREE 한도를 적용한다")
    void expired_pro_uses_free_limit() {
        User user = new User("expired-user", "encoded");
        user.activateSubscription(
                SubscriptionPlan.PRO,
                NOW.minusSeconds(3600),
                NOW.minusSeconds(1)
        );
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThat(policy.dailyLimitFor(1L)).isEqualTo(5);
    }

    @Test
    @DisplayName("아직 시작하지 않은 PRO 구독은 FREE 한도를 적용한다")
    void future_pro_uses_free_limit() {
        User user = new User("future-user", "encoded");
        user.activateSubscription(
                SubscriptionPlan.PRO,
                NOW.plusSeconds(1),
                NOW.plusSeconds(3600)
        );
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThat(policy.dailyLimitFor(1L)).isEqualTo(5);
    }
}
