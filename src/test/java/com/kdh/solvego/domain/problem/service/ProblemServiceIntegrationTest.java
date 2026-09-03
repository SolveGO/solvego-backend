package com.kdh.solvego.domain.problem.service;

import com.kdh.solvego.domain.attempt.entity.Attempt;
import com.kdh.solvego.domain.attempt.repository.AttemptRepository;
import com.kdh.solvego.domain.common.vo.Position;
import com.kdh.solvego.domain.problem.dto.*;
import com.kdh.solvego.domain.problem.entity.PlayerColor;
import com.kdh.solvego.domain.problem.entity.Problem;
import com.kdh.solvego.domain.problem.exception.ProblemOwnershipException;
import com.kdh.solvego.domain.problem.exception.ProblemNotFoundException;
import com.kdh.solvego.domain.problem.repository.ProblemRepository;
import com.kdh.solvego.domain.user.entity.User;
import com.kdh.solvego.domain.user.exception.UserNotFoundException;
import com.kdh.solvego.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProblemServiceIntegrationTest {

    @Autowired
    private ProblemService problemService;

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AttemptRepository attemptRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("문제를 등록하면 DB에 저장되고 다시 조회할 수 있다")
    void create_problem_success() {
        // given
        User creator = userRepository.save(new User("creator", "encoded-password"));

        ProblemCreateRequest request = new ProblemCreateRequest(
                "problem",
                "description",
                List.of(new Position(3, 3), new Position(4, 4)),
                List.of(new Position(5, 5)),
                PlayerColor.BLACK,
                new Position(10, 10)
        );

        // when
        ProblemCreateResponse response =
                problemService.createProblem(creator.getId(), request);

        entityManager.flush();
        entityManager.clear();

        // then
        Problem savedProblem = problemRepository.findById(response.problemId())
                .orElseThrow();

        assertThat(savedProblem.getTitle()).isEqualTo("problem");
        assertThat(savedProblem.getDescription()).isEqualTo("description");

        assertThat(savedProblem.getBlackStones())
                .containsExactly(
                        new Position(3, 3),
                        new Position(4, 4)
                );

        assertThat(savedProblem.getWhiteStones())
                .containsExactly(new Position(5, 5));

        assertThat(savedProblem.getNextPlayer()).isEqualTo(PlayerColor.BLACK);
        assertThat(savedProblem.isCorrectPosition(new Position(10, 10))).isTrue();
        assertThat(savedProblem.isCorrectPosition(new Position(1, 1))).isFalse();

        assertThat(savedProblem.getCreator().getUsername()).isEqualTo("creator");
    }

    @Test
    @DisplayName("문제 상세를 조회한다")
    void get_problem_success() {
        // given
        User creator = userRepository.save(new User("creator", "encoded-password"));

        Problem problem = problemRepository.save(new Problem(
                "problem",
                "description",
                List.of(new Position(3, 3)),
                List.of(new Position(4, 4)),
                PlayerColor.BLACK,
                new Position(10, 10),
                creator
        ));

        Long problemId = problem.getId();

        entityManager.flush();
        entityManager.clear();

        // when
        ProblemDetailResponse response = problemService.getProblem(problemId);

        // then
        assertThat(response.problemId()).isEqualTo(problemId);
        assertThat(response.title()).isEqualTo("problem");
        assertThat(response.description()).isEqualTo("description");
        assertThat(response.blackStones()).containsExactly(new Position(3, 3));
        assertThat(response.whiteStones()).containsExactly(new Position(4, 4));
        assertThat(response.nextPlayer()).isEqualTo(PlayerColor.BLACK);
        assertThat(response.creatorName()).isEqualTo("creator");
    }

    @Test
    @DisplayName("문제 목록을 조회한다")
    void get_problems_success() {
        // given
        User creator = userRepository.save(new User("creator", "encoded-password"));

        problemRepository.save(new Problem(
                "problem",
                "description",
                List.of(new Position(3, 3)),
                List.of(new Position(4, 4)),
                PlayerColor.BLACK,
                new Position(10, 10),
                creator
        ));

        entityManager.flush();
        entityManager.clear();

        // when
        ProblemListResponse response = problemService.getProblems();

        // then
        assertThat(response.problems()).hasSize(1);
        assertThat(response.problems().get(0).title()).isEqualTo("problem");
        assertThat(response.problems().get(0).creatorName()).isEqualTo("creator");
    }

    @Test
    @DisplayName("존재하지 않는 userId로 문제를 등록하면 예외가 발생한다")
    void create_problem_fails_when_user_not_found() {
        // given
        ProblemCreateRequest request = new ProblemCreateRequest(
                "problem",
                "description",
                List.of(new Position(3, 3)),
                List.of(new Position(4, 4)),
                PlayerColor.BLACK,
                new Position(10, 10)
        );

        // when & then
        assertThatThrownBy(() -> problemService.createProblem(999L, request))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    @DisplayName("존재하지 않는 problemId로 상세 조회하면 예외가 발생한다")
    void get_problem_fails_when_problem_not_found() {
        // when & then
        assertThatThrownBy(() -> problemService.getProblem(999L))
                .isInstanceOf(ProblemNotFoundException.class);
    }

    @Test
    @DisplayName("문제를 수정하면 변경 내용이 DB에 반영된다")
    void update_problem_success() {
        // given
        User creator = userRepository.save(
                new User("creator", "encoded-password")
        );

        Problem problem = problemRepository.save(new Problem(
                "old problem",
                "old description",
                List.of(new Position(3, 3)),
                List.of(new Position(4, 4)),
                PlayerColor.BLACK,
                new Position(10, 10),
                creator
        ));

        Long problemId = problem.getId();

        ProblemUpdateRequest request = new ProblemUpdateRequest(
                "updated problem",
                "updated description",
                List.of(new Position(5, 5), new Position(6, 6)),
                List.of(new Position(7, 7)),
                PlayerColor.WHITE,
                new Position(11, 11)
        );

        entityManager.flush();
        entityManager.clear();

        // when
        problemService.updateProblem(
                creator.getId(),
                problemId,
                request
        );

        entityManager.flush();
        entityManager.clear();

        // then
        Problem updatedProblem = problemRepository.findById(problemId)
                .orElseThrow();

        assertThat(updatedProblem.getTitle())
                .isEqualTo("updated problem");

        assertThat(updatedProblem.getDescription())
                .isEqualTo("updated description");

        assertThat(updatedProblem.getBlackStones())
                .containsExactly(
                        new Position(5, 5),
                        new Position(6, 6)
                );

        assertThat(updatedProblem.getWhiteStones())
                .containsExactly(new Position(7, 7));

        assertThat(updatedProblem.getNextPlayer())
                .isEqualTo(PlayerColor.WHITE);

        assertThat(updatedProblem.isCorrectPosition(new Position(11, 11)))
                .isTrue();

        assertThat(updatedProblem.getCreator().getId())
                .isEqualTo(creator.getId());
    }

    @Test
    @DisplayName("존재하지 않는 문제를 수정하면 예외가 발생한다")
    void update_problem_fails_when_problem_not_found() {
        // given
        User creator = userRepository.save(
                new User("creator", "encoded-password")
        );

        ProblemUpdateRequest request = new ProblemUpdateRequest(
                "updated problem",
                "updated description",
                List.of(new Position(5, 5)),
                List.of(new Position(6, 6)),
                PlayerColor.WHITE,
                new Position(11, 11)
        );

        // when & then
        assertThatThrownBy(
                () -> problemService.updateProblem(
                        creator.getId(),
                        999L,
                        request
                )
        )
                .isInstanceOf(ProblemNotFoundException.class);
    }

    @Test
    @DisplayName("문제 작성자가 아니면 문제를 수정할 수 없다")
    void update_problem_fails_when_user_is_not_creator() {
        // given
        User creator = userRepository.save(
                new User("creator", "encoded-password")
        );

        User otherUser = userRepository.save(
                new User("other-user", "encoded-password")
        );

        Problem problem = problemRepository.save(new Problem(
                "old problem",
                "old description",
                List.of(new Position(3, 3)),
                List.of(new Position(4, 4)),
                PlayerColor.BLACK,
                new Position(10, 10),
                creator
        ));

        Long problemId = problem.getId();

        ProblemUpdateRequest request = new ProblemUpdateRequest(
                "updated problem",
                "updated description",
                List.of(new Position(5, 5)),
                List.of(new Position(6, 6)),
                PlayerColor.WHITE,
                new Position(11, 11)
        );

        entityManager.flush();
        entityManager.clear();

        // when & then
        assertThatThrownBy(
                () -> problemService.updateProblem(
                        otherUser.getId(),
                        problemId,
                        request
                )
        )
                .isInstanceOf(ProblemOwnershipException.class);

        entityManager.clear();

        Problem unchangedProblem = problemRepository.findById(problemId)
                .orElseThrow();

        assertThat(unchangedProblem.getTitle())
                .isEqualTo("old problem");

        assertThat(unchangedProblem.getDescription())
                .isEqualTo("old description");

        assertThat(unchangedProblem.getNextPlayer())
                .isEqualTo(PlayerColor.BLACK);
    }

    @Test
    @DisplayName("문제를 삭제하면 문제와 관련 풀이 기록이 DB에서 삭제된다")
    void delete_problem_success() {
        // given
        User creator = userRepository.save(
                new User("creator", "encoded-password")
        );

        User solver = userRepository.save(
                new User("solver", "encoded-password")
        );

        Problem problem = problemRepository.save(new Problem(
                "problem",
                "description",
                List.of(new Position(3, 3)),
                List.of(new Position(4, 4)),
                PlayerColor.BLACK,
                new Position(10, 10),
                creator
        ));

        Attempt attempt = attemptRepository.save(new Attempt(
                solver,
                problem,
                new Position(1, 1),
                false
        ));

        Long problemId = problem.getId();
        Long attemptId = attempt.getId();

        entityManager.flush();
        entityManager.clear();

        // when
        problemService.deleteProblem(creator.getId(), problemId);

        entityManager.flush();
        entityManager.clear();

        // then
        assertThat(problemRepository.findById(problemId)).isEmpty();
        assertThat(attemptRepository.findById(attemptId)).isEmpty();
    }
    @Test
    @DisplayName("문제 작성자가 아니면 문제를 삭제할 수 없다")
    void delete_problem_fails_when_user_is_not_creator() {
        // given
        User creator = userRepository.save(
                new User("creator", "encoded-password")
        );

        User otherUser = userRepository.save(
                new User("other-user", "encoded-password")
        );

        Problem problem = problemRepository.save(new Problem(
                "problem",
                "description",
                List.of(new Position(3, 3)),
                List.of(new Position(4, 4)),
                PlayerColor.BLACK,
                new Position(10, 10),
                creator
        ));

        Long problemId = problem.getId();

        entityManager.flush();
        entityManager.clear();

        // when & then
        assertThatThrownBy(
                () -> problemService.deleteProblem(
                        otherUser.getId(),
                        problemId
                )
        )
                .isInstanceOf(ProblemOwnershipException.class);

        entityManager.clear();

        assertThat(problemRepository.findById(problemId)).isPresent();
    }
    @Test
    @DisplayName("존재하지 않는 문제를 삭제하면 예외가 발생한다")
    void delete_problem_fails_when_problem_not_found() {
        // given
        User creator = userRepository.save(
                new User("creator", "encoded-password")
        );

        // when & then
        assertThatThrownBy(
                () -> problemService.deleteProblem(
                        creator.getId(),
                        999L
                )
        )
                .isInstanceOf(ProblemNotFoundException.class);
    }

    @Test
    @DisplayName("문제 작성자는 정답 좌표가 포함된 수정용 문제 정보를 조회할 수 있다")
    void get_problem_for_edit_success() {
        // given
        User creator = userRepository.save(
                new User("creator", "encoded-password")
        );

        Problem problem = problemRepository.save(new Problem(
                "problem",
                "description",
                List.of(new Position(3, 3)),
                List.of(new Position(4, 4)),
                PlayerColor.BLACK,
                new Position(10, 10),
                creator
        ));

        Long problemId = problem.getId();

        entityManager.flush();
        entityManager.clear();

        // when
        ProblemEditResponse response =
                problemService.getProblemForEdit(
                        creator.getId(),
                        problemId
                );

        // then
        assertThat(response.problemId()).isEqualTo(problemId);
        assertThat(response.title()).isEqualTo("problem");
        assertThat(response.description()).isEqualTo("description");
        assertThat(response.blackStones())
                .containsExactly(new Position(3, 3));
        assertThat(response.whiteStones())
                .containsExactly(new Position(4, 4));
        assertThat(response.nextPlayer())
                .isEqualTo(PlayerColor.BLACK);
        assertThat(response.answerPosition())
                .isEqualTo(new Position(10, 10));
    }
    @Test
    @DisplayName("문제 작성자가 아니면 수정용 문제 정보를 조회할 수 없다")
    void get_problem_for_edit_fails_when_user_is_not_creator() {
        // given
        User creator = userRepository.save(
                new User("creator", "encoded-password")
        );

        User otherUser = userRepository.save(
                new User("other-user", "encoded-password")
        );

        Problem problem = problemRepository.save(new Problem(
                "problem",
                "description",
                List.of(new Position(3, 3)),
                List.of(new Position(4, 4)),
                PlayerColor.BLACK,
                new Position(10, 10),
                creator
        ));

        Long problemId = problem.getId();

        entityManager.flush();
        entityManager.clear();

        // when & then
        assertThatThrownBy(
                () -> problemService.getProblemForEdit(
                        otherUser.getId(),
                        problemId
                )
        )
                .isInstanceOf(ProblemOwnershipException.class);
    }
}