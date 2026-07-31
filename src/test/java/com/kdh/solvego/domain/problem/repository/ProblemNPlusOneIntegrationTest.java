package com.kdh.solvego.domain.problem.repository;

import com.kdh.solvego.domain.common.vo.Position;
import com.kdh.solvego.domain.problem.entity.PlayerColor;
import com.kdh.solvego.domain.problem.entity.Problem;
import com.kdh.solvego.domain.user.entity.User;
import com.kdh.solvego.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProblemNPlusOneIntegrationTest {

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("Fetch Join 적용 전 문제 목록 조회 시 N+1 쿼리가 발생한다")
    void findAll_withoutFetchJoin_causesNPlusOne() {
        // given
        User userA = userRepository.save(
                new User("n-plus-one-user-a", "password")
        );
        User userB = userRepository.save(
                new User("n-plus-one-user-b", "password")
        );
        User userC = userRepository.save(
                new User("n-plus-one-user-c", "password")
        );

        Problem problemA = problemRepository.save(
                createProblem("문제 A", userA)
        );
        Problem problemB = problemRepository.save(
                createProblem("문제 B", userB)
        );
        Problem problemC = problemRepository.save(
                createProblem("문제 C", userC)
        );

        Set<Long> createdProblemIds = Set.of(
                problemA.getId(),
                problemB.getId(),
                problemC.getId()
        );

        /*
         * 저장한 INSERT 쿼리를 DB에 반영한다.
         */
        entityManager.flush();

        /*
         * 영속성 컨텍스트를 초기화한다.
         *
         * 이를 생략하면 방금 저장한 User와 Problem이 1차 캐시에 남아 있어
         * LAZY 로딩 시 추가 SELECT가 발생하지 않을 수 있다.
         */
        entityManager.clear();

        printStartLine("BEFORE: N+1");

        // when
        /*
         * Fetch Join이 없는 JpaRepository 기본 findAll()을 사용한다.
         *
         * 이 시점에는 Problem 목록을 조회하는 SELECT 1개만 실행된다.
         */
        List<Problem> problems = problemRepository.findAll();

        /*
         * 테스트에서 생성한 Problem 3개만 추출한다.
         *
         * getId()는 Problem 자체의 필드이므로
         * 이 과정에서는 creator 조회 쿼리가 실행되지 않는다.
         */
        List<Problem> createdProblems = problems.stream()
                .filter(problem -> createdProblemIds.contains(problem.getId()))
                .toList();

        /*
         * creator는 LAZY 연관관계이다.
         *
         * 서로 다른 User를 참조하는 Problem 3개의 creator에 접근하므로
         * User 조회 SELECT가 3번 추가로 실행된다.
         */
        List<String> creatorNames = createdProblems.stream()
                .map(problem -> problem.getCreator().getUsername())
                .toList();

        printEndLine();

        // then
        assertThat(createdProblems).hasSize(3);
        assertThat(creatorNames)
                .containsExactlyInAnyOrder(
                        "n-plus-one-user-a",
                        "n-plus-one-user-b",
                        "n-plus-one-user-c"
                );
    }

    @Test
    @DisplayName("Fetch Join 적용 후 문제와 작성자를 하나의 쿼리로 조회한다")
    void findAll_withFetchJoin_executesSingleQuery() {
        // given
        User userA = userRepository.save(
                new User("fetch-join-user-a", "password")
        );
        User userB = userRepository.save(
                new User("fetch-join-user-b", "password")
        );
        User userC = userRepository.save(
                new User("fetch-join-user-c", "password")
        );

        Problem problemA = problemRepository.save(
                createProblem("문제 A", userA)
        );
        Problem problemB = problemRepository.save(
                createProblem("문제 B", userB)
        );
        Problem problemC = problemRepository.save(
                createProblem("문제 C", userC)
        );

        Set<Long> createdProblemIds = Set.of(
                problemA.getId(),
                problemB.getId(),
                problemC.getId()
        );

        entityManager.flush();
        entityManager.clear();

        printStartLine("AFTER: FETCH JOIN");

        // when
        /*
         * ProblemRepository에 이미 정의된 Fetch Join 메서드를 사용한다.
         *
         * Problem과 creator를 하나의 SELECT로 함께 조회한다.
         */
        List<Problem> problems =
                problemRepository.findAllWithCreatorOrderByIdDesc();

        List<Problem> createdProblems = problems.stream()
                .filter(problem -> createdProblemIds.contains(problem.getId()))
                .toList();

        /*
         * creator가 이미 Fetch Join으로 조회되었으므로
         * getUsername()에 접근해도 추가 SELECT가 실행되지 않는다.
         */
        List<String> creatorNames = createdProblems.stream()
                .map(problem -> problem.getCreator().getUsername())
                .toList();

        printEndLine();

        // then
        assertThat(createdProblems).hasSize(3);
        assertThat(creatorNames)
                .containsExactlyInAnyOrder(
                        "fetch-join-user-a",
                        "fetch-join-user-b",
                        "fetch-join-user-c"
                );
    }

    private Problem createProblem(String title, User creator) {
        return new Problem(
                title,
                "N+1 테스트를 위한 문제입니다.",
                List.of(
                        new Position(3, 3),
                        new Position(4, 4)
                ),
                List.of(
                        new Position(5, 5),
                        new Position(6, 6)
                ),
                PlayerColor.BLACK,
                new Position(7, 7),
                creator
        );
    }

    private void printStartLine(String title) {
        System.out.println();
        System.out.println("==================================================");
        System.out.println(title);
        System.out.println("==================================================");
    }

    private void printEndLine() {
        System.out.println("==================================================");
        System.out.println();
    }
}