package com.kdh.solvego.domain.user.repository;

import com.kdh.solvego.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

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

}
