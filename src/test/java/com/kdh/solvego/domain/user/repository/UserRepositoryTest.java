package com.kdh.solvego.domain.user.repository;

import com.kdh.solvego.domain.user.entity.User;
import com.kdh.solvego.domain.user.type.SubscriptionPlan;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("username이 존재하면 true를 반환한다")
    void exists_by_username_returns_true() {
        // given
        User user = new User("username", "encoded-password");
        userRepository.save(user);

        // when
        boolean exists = userRepository.existsByUsername("username");

        // then
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("username이 존재하지 않으면 false를 반환한다")
    void exists_by_username_returns_false() {
        // when
        boolean exists = userRepository.existsByUsername("unknown");

        // then
        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("username으로 사용자를 조회할 수 있다")
    void find_by_username_returns_user() {
        // given
        User user = new User("username", "encoded-password");
        userRepository.save(user);

        // when
        Optional<User> foundUser = userRepository.findByUsername("username");

        // then
        assertThat(foundUser).isPresent();
        assertThat(foundUser.get().getUsername()).isEqualTo("username");
    }

    @Test
    @DisplayName("존재하지 않는 username이면 Optional.empty를 반환한다")
    void find_by_username_returns_empty() {
        // when
        Optional<User> foundUser = userRepository.findByUsername("unknown");

        // then
        assertThat(foundUser).isEmpty();
    }

    @Test
    @DisplayName("신규 사용자는 FREE이고 활성화된 PRO 구독은 저장된다")
    void subscription_plan_is_persisted() {
        User user = userRepository.save(new User("subscriber", "encoded-password"));

        assertThat(user.getCurrentPlan(Instant.parse("2026-09-16T00:00:00Z")))
                .isEqualTo(SubscriptionPlan.FREE);

        user.activateSubscription(
                SubscriptionPlan.PRO,
                Instant.parse("2026-09-01T00:00:00Z"),
                Instant.parse("2026-10-01T00:00:00Z")
        );
        userRepository.flush();
        entityManager.clear();

        User found = userRepository.findById(user.getId()).orElseThrow();
        assertThat(found.getCurrentPlan(Instant.parse("2026-09-16T00:00:00Z")))
                .isEqualTo(SubscriptionPlan.PRO);
    }
}
