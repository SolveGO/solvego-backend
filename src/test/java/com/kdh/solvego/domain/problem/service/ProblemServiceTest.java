package com.kdh.solvego.domain.problem.service;

import com.kdh.solvego.domain.common.vo.Position;
import com.kdh.solvego.domain.problem.dto.*;
import com.kdh.solvego.domain.problem.entity.PlayerColor;
import com.kdh.solvego.domain.problem.entity.Problem;
import com.kdh.solvego.domain.problem.exception.ProblemAccessDeniedException;
import com.kdh.solvego.domain.problem.exception.ProblemNotFoundException;
import com.kdh.solvego.domain.problem.mapper.ProblemMapper;
import com.kdh.solvego.domain.problem.repository.ProblemRepository;
import com.kdh.solvego.domain.user.entity.User;
import com.kdh.solvego.domain.user.exception.UserNotFoundException;
import com.kdh.solvego.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ActiveProfiles("test")
@ExtendWith(MockitoExtension.class)
class ProblemServiceTest {

    @Mock
    private ProblemRepository problemRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProblemMapper problemMapper;

    @InjectMocks
    private ProblemService problemService;

    @Test
    @DisplayName("문제 등록에 성공한다")
    void create_problem_success() {
        // given
        Long userId = 1L;

        User creator = new User("creator", "encoded-password");
        ReflectionTestUtils.setField(creator, "id", userId);

        ProblemCreateRequest request = createProblemCreateRequest();

        Problem problem = createProblem(creator, "problem");

        Problem savedProblem = createProblem(creator, "problem");
        ReflectionTestUtils.setField(savedProblem, "id", 10L);

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(creator));

        when(problemMapper.toEntity(request, creator))
                .thenReturn(problem);

        when(problemRepository.save(problem))
                .thenReturn(savedProblem);

        // when
        ProblemCreateResponse response = problemService.createProblem(userId, request);

        // then
        assertThat(response.problemId()).isEqualTo(10L);

        verify(userRepository).findById(userId);
        verify(problemMapper).toEntity(request, creator);
        verify(problemRepository).save(problem);
    }

    @Test
    @DisplayName("존재하지 않는 userId로 문제를 등록하면 예외가 발생한다")
    void create_problem_fails_when_user_not_found() {
        // given
        Long userId = 999L;
        ProblemCreateRequest request = createProblemCreateRequest();

        when(userRepository.findById(userId))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> problemService.createProblem(userId, request))
                .isInstanceOf(UserNotFoundException.class);

        verify(userRepository).findById(userId);
        verifyNoInteractions(problemMapper);
        verify(problemRepository, never()).save(any(Problem.class));
    }

    @Test
    @DisplayName("문제 목록을 조회한다")
    void get_problems_success() {
        // given
        User creator = new User("creator", "encoded-password");

        Problem problem = createProblem(creator, "problem");
        ReflectionTestUtils.setField(problem, "id", 1L);

        List<Problem> problems = List.of(problem);

        ProblemListResponse expectedResponse = new ProblemListResponse(
                List.of(new ProblemSummaryResponse(1L, "problem", "creator"))
        );

        when(problemRepository.findAllWithCreatorOrderByIdDesc())
                .thenReturn(problems);

        when(problemMapper.toListResponse(problems))
                .thenReturn(expectedResponse);

        // when
        ProblemListResponse response = problemService.getProblems();

        // then
        assertThat(response).isEqualTo(expectedResponse);

        verify(problemRepository).findAllWithCreatorOrderByIdDesc();
        verify(problemMapper).toListResponse(problems);
    }

    @Test
    @DisplayName("문제가 하나도 없으면 빈 목록을 반환한다")
    void get_problems_empty_returns_empty_list() {
        // given
        List<Problem> problems = List.of();

        ProblemListResponse expectedResponse = new ProblemListResponse(List.of());

        when(problemRepository.findAllWithCreatorOrderByIdDesc())
                .thenReturn(problems);

        when(problemMapper.toListResponse(problems))
                .thenReturn(expectedResponse);

        // when
        ProblemListResponse response = problemService.getProblems();

        // then
        assertThat(response.problems()).isEmpty();

        verify(problemRepository).findAllWithCreatorOrderByIdDesc();
        verify(problemMapper).toListResponse(problems);
    }

    @Test
    @DisplayName("문제 상세를 조회한다")
    void get_problem_success() {
        // given
        Long problemId = 1L;

        User creator = new User("creator", "encoded-password");

        Problem problem = createProblem(creator, "problem");
        ReflectionTestUtils.setField(problem, "id", problemId);

        ProblemDetailResponse expectedResponse = new ProblemDetailResponse(
                problemId,
                "problem",
                "description",
                List.of(new Position(3, 3)),
                List.of(new Position(4, 4)),
                PlayerColor.BLACK,
                "creator"
        );

        when(problemRepository.findByIdWithCreator(problemId))
                .thenReturn(Optional.of(problem));

        when(problemMapper.toDetailResponse(problem))
                .thenReturn(expectedResponse);

        // when
        ProblemDetailResponse response = problemService.getProblem(problemId);

        // then
        assertThat(response).isEqualTo(expectedResponse);

        verify(problemRepository).findByIdWithCreator(problemId);
        verify(problemMapper).toDetailResponse(problem);
    }

    @Test
    @DisplayName("존재하지 않는 problemId이면 예외가 발생한다")
    void get_problem_fails_when_problem_not_found() {
        // given
        Long problemId = 999L;

        when(problemRepository.findByIdWithCreator(problemId))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> problemService.getProblem(problemId))
                .isInstanceOf(ProblemNotFoundException.class);

        verify(problemRepository).findByIdWithCreator(problemId);
        verifyNoInteractions(problemMapper);
    }

    @Test
    @DisplayName("문제 작성자는 문제를 수정할 수 있다")
    void update_problem_success() {
        // given
        Long userId = 1L;
        Long problemId = 10L;

        User creator = new User("creator", "encoded-password");
        ReflectionTestUtils.setField(creator, "id", userId);

        Problem problem = createProblem(creator, "old problem");
        ReflectionTestUtils.setField(problem, "id", problemId);

        ProblemUpdateRequest request = createProblemUpdateRequest();

        when(problemRepository.findByIdWithCreator(problemId))
                .thenReturn(Optional.of(problem));

        // when
        problemService.updateProblem(userId, problemId, request);

        // then
        assertThat(problem.getTitle()).isEqualTo("updated problem");
        assertThat(problem.getDescription()).isEqualTo("updated description");
        assertThat(problem.getBlackStones())
                .containsExactly(new Position(5, 5));
        assertThat(problem.getWhiteStones())
                .containsExactly(new Position(6, 6));
        assertThat(problem.getNextPlayer()).isEqualTo(PlayerColor.WHITE);
        assertThat(problem.getAnswerPosition())
                .isEqualTo(new Position(11, 11));

        verify(problemRepository).findByIdWithCreator(problemId);
        verify(problemRepository, never()).save(any(Problem.class));
        verifyNoInteractions(userRepository, problemMapper);
    }

    @Test
    @DisplayName("존재하지 않는 문제를 수정하면 예외가 발생한다")
    void update_problem_fails_when_problem_not_found() {
        // given
        Long userId = 1L;
        Long problemId = 999L;

        ProblemUpdateRequest request = createProblemUpdateRequest();

        when(problemRepository.findByIdWithCreator(problemId))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(
                () -> problemService.updateProblem(userId, problemId, request)
        )
                .isInstanceOf(ProblemNotFoundException.class);

        verify(problemRepository).findByIdWithCreator(problemId);
        verify(problemRepository, never()).save(any(Problem.class));
        verifyNoInteractions(userRepository, problemMapper);
    }

    @Test
    @DisplayName("문제 작성자가 아니면 문제를 수정할 수 없다")
    void update_problem_fails_when_user_is_not_creator() {
        // given
        Long creatorId = 1L;
        Long otherUserId = 2L;
        Long problemId = 10L;

        User creator = new User("creator", "encoded-password");
        ReflectionTestUtils.setField(creator, "id", creatorId);

        Problem problem = createProblem(creator, "old problem");
        ReflectionTestUtils.setField(problem, "id", problemId);

        ProblemUpdateRequest request = createProblemUpdateRequest();

        when(problemRepository.findByIdWithCreator(problemId))
                .thenReturn(Optional.of(problem));

        // when & then
        assertThatThrownBy(
                () -> problemService.updateProblem(otherUserId, problemId, request)
        )
                .isInstanceOf(ProblemAccessDeniedException.class);

        assertThat(problem.getTitle()).isEqualTo("old problem");
        assertThat(problem.getDescription()).isEqualTo("description");
        assertThat(problem.getNextPlayer()).isEqualTo(PlayerColor.BLACK);

        verify(problemRepository).findByIdWithCreator(problemId);
        verify(problemRepository, never()).save(any(Problem.class));
        verifyNoInteractions(userRepository, problemMapper);
    }

    private ProblemCreateRequest createProblemCreateRequest() {
        return new ProblemCreateRequest(
                "problem",
                "description",
                List.of(new Position(3, 3)),
                List.of(new Position(4, 4)),
                PlayerColor.BLACK,
                new Position(10, 10)
        );
    }

    private Problem createProblem(User creator, String title) {
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

    private ProblemUpdateRequest createProblemUpdateRequest() {
        return new ProblemUpdateRequest(
                "updated problem",
                "updated description",
                List.of(new Position(5, 5)),
                List.of(new Position(6, 6)),
                PlayerColor.WHITE,
                new Position(11, 11)
        );
    }
}