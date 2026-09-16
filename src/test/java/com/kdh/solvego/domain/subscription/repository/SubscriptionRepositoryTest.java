package com.kdh.solvego.domain.subscription.repository;

import com.kdh.solvego.domain.subscription.entity.Subscription;
import com.kdh.solvego.domain.subscription.type.SubscriptionPlan;
import com.kdh.solvego.domain.user.entity.User;
import com.kdh.solvego.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SubscriptionRepositoryTest {
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("사용자별 구독과 PRO 기간을 저장한다")
    void subscription_is_persisted_for_user() {
        User user = userRepository.save(new User("subscriber", "encoded"));
        Subscription subscription = Subscription.free(user);
        subscription.activatePro(
                Instant.parse("2026-09-01T00:00:00Z"),
                Instant.parse("2026-10-01T00:00:00Z")
        );
        subscriptionRepository.saveAndFlush(subscription);
        entityManager.clear();

        Subscription found = subscriptionRepository.findByUserId(user.getId())
                .orElseThrow();

        assertThat(found.getPlan()).isEqualTo(SubscriptionPlan.PRO);
        assertThat(found.effectivePlan(Instant.parse("2026-09-16T00:00:00Z")))
                .isEqualTo(SubscriptionPlan.PRO);
    }
}
