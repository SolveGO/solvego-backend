package com.kdh.solvego.domain.user.service;

import com.kdh.solvego.domain.attempt.entity.Attempt;
import com.kdh.solvego.domain.attempt.repository.AttemptRepository;
import com.kdh.solvego.domain.common.vo.Position;
import com.kdh.solvego.domain.problem.entity.PlayerColor;
import com.kdh.solvego.domain.problem.entity.Problem;
import com.kdh.solvego.domain.problem.repository.ProblemRepository;
import com.kdh.solvego.domain.user.dto.MyPageResponse;
import com.kdh.solvego.domain.user.dto.PasswordChangeRequest;
import com.kdh.solvego.domain.user.dto.SignupRequest;
import com.kdh.solvego.domain.user.dto.SignupResponse;
import com.kdh.solvego.domain.user.entity.User;
import com.kdh.solvego.domain.user.type.SubscriptionPlan;
import com.kdh.solvego.domain.user.exception.DuplicateUsernameException;
import com.kdh.solvego.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserServiceIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private AttemptRepository attemptRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("회원가입에 성공하면 User가 DB에 저장되고 비밀번호가 암호화된다")
    void signup_success() {
        // given
        SignupRequest request = new SignupRequest("username", "1234");

        // when
        SignupResponse response = userService.signup(request);

        entityManager.flush();
        entityManager.clear();

        // then
        User savedUser = userRepository.findById(response.userId())
                .orElseThrow();

        assertThat(savedUser.getUsername()).isEqualTo("username");

        assertThat(savedUser.matchesPassword("1234", passwordEncoder))
                .isTrue();

        assertThat(savedUser.matchesPassword("wrong-password", passwordEncoder))
                .isFalse();
    }

    @Test
    @DisplayName("중복된 username으로 회원가입하면 예외가 발생한다")
    void signup_fails_when_username_is_duplicated() {
        // given
        userService.signup(new SignupRequest("username", "1234"));

        SignupRequest duplicatedRequest = new SignupRequest("username", "5678");

        // when & then
        assertThatThrownBy(() -> userService.signup(duplicatedRequest))
                .isInstanceOf(DuplicateUsernameException.class);
    }

    @Test
    @DisplayName("마이페이지 집계와 비밀번호 변경이 실제 데이터에 반영된다")
    void my_page_and_password_change_use_persisted_data() {
        Long userId = userService.signup(new SignupRequest("username", "1234")).userId();
        User user = userRepository.findById(userId).orElseThrow();
        Problem problem = problemRepository.save(problem("내 문제", user));
        attemptRepository.save(new Attempt(
                user,
                problem,
                new Position(1, 1),
                false
        ));
        entityManager.flush();
        entityManager.clear();

        MyPageResponse response = userService.getMyPage(userId);
        userService.changePassword(
                userId,
                new PasswordChangeRequest("1234", "changed-password")
        );
        entityManager.flush();
        entityManager.clear();

        assertThat(response.username()).isEqualTo("username");
        assertThat(response.registeredProblemCount()).isEqualTo(1);
        assertThat(response.solvedProblemCount()).isEqualTo(1);
        assertThat(response.wrongProblemCount()).isEqualTo(1);
        assertThat(response.plan()).isEqualTo(SubscriptionPlan.FREE);
        assertThat(response.problems().get(0).problemId()).isEqualTo(problem.getId());
        User changedUser = userRepository.findById(userId).orElseThrow();
        assertThat(changedUser.matchesPassword("changed-password", passwordEncoder)).isTrue();
        assertThat(changedUser.matchesPassword("1234", passwordEncoder)).isFalse();
    }

    @Test
    @DisplayName("회원 탈퇴 시 사용자와 연결된 풀이와 등록 문제를 FK 순서대로 삭제한다")
    void delete_account_preserves_foreign_key_integrity() {
        Long deletedUserId = userService.signup(
                new SignupRequest("deleted-user", "1234")
        ).userId();
        Long remainingUserId = userService.signup(
                new SignupRequest("remaining-user", "1234")
        ).userId();
        User deletedUser = userRepository.findById(deletedUserId).orElseThrow();
        User remainingUser = userRepository.findById(remainingUserId).orElseThrow();
        Problem deletedProblem = problemRepository.save(problem("삭제할 문제", deletedUser));
        Problem remainingProblem = problemRepository.save(problem("남을 문제", remainingUser));
        attemptRepository.save(new Attempt(
                remainingUser,
                deletedProblem,
                new Position(1, 1),
                false
        ));
        attemptRepository.save(new Attempt(
                deletedUser,
                remainingProblem,
                new Position(2, 2),
                false
        ));
        entityManager.flush();
        entityManager.clear();

        userService.deleteAccount(deletedUserId);
        entityManager.flush();
        entityManager.clear();

        assertThat(userRepository.findById(deletedUserId)).isEmpty();
        assertThat(problemRepository.findById(deletedProblem.getId())).isEmpty();
        assertThat(problemRepository.findById(remainingProblem.getId())).isPresent();
        assertThat(attemptRepository.findAll()).isEmpty();
    }

    private Problem problem(String title, User creator) {
        return new Problem(
                title,
                "description",
                List.of(new Position(3, 3)),
                List.of(new Position(4, 4)),
                PlayerColor.BLACK,
                new Position(10, 10),
                creator
        );
    }
}
