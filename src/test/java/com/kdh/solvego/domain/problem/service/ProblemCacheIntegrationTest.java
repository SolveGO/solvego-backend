package com.kdh.solvego.domain.problem.service;

import com.kdh.solvego.domain.attempt.repository.AttemptRepository;
import com.kdh.solvego.domain.common.vo.Position;
import com.kdh.solvego.domain.problem.dto.ProblemUpdateRequest;
import com.kdh.solvego.domain.problem.entity.PlayerColor;
import com.kdh.solvego.domain.problem.entity.Problem;
import com.kdh.solvego.domain.problem.repository.ProblemRepository;
import com.kdh.solvego.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(
        properties = "spring.cache.type=redis"
)
@ActiveProfiles("test")
@Testcontainers
class ProblemCacheIntegrationTest {

    private static final String CACHE_NAME = "problemPages";

    @Container
    static final GenericContainer<?> redisContainer =
            new GenericContainer<>(
                    DockerImageName.parse("redis:7.4-alpine")
            )
                    .withExposedPorts(6379);

    @DynamicPropertySource
    static void register_redis_properties(
            DynamicPropertyRegistry registry
    ) {
        registry.add(
                "spring.data.redis.host",
                redisContainer::getHost
        );

        registry.add(
                "spring.data.redis.port",
                () -> redisContainer.getMappedPort(6379)
        );
    }

    @Autowired
    private ProblemService problemService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @MockitoBean
    private ProblemRepository problemRepository;

    @MockitoBean
    private AttemptRepository attemptRepository;

    @BeforeEach
    void set_up() {
        redisTemplate
                .getConnectionFactory()
                .getConnection()
                .serverCommands()
                .flushDb();

        clearInvocations(
                problemRepository,
                attemptRepository
        );

        given(
                problemRepository.findAllWithCreatorOrderByIdDesc(
                        any(Pageable.class)
                )
        )
                .willAnswer(invocation -> {
                    Pageable pageable = invocation.getArgument(0);

                    return new PageImpl<Problem>(
                            List.of(),
                            pageable,
                            0
                    );
                });
    }

    @Test
    @DisplayName("같은 캐시 대상 페이지를 두 번 조회하면 Repository는 한 번만 호출된다")
    void get_problems_uses_cache_when_same_page_is_requested_twice() {
        // given
        int page = 0;
        int size = 20;

        // when
        problemService.getProblems(page, size);
        problemService.getProblems(page, size);

        // then
        verify(
                problemRepository,
                times(1)
        )
                .findAllWithCreatorOrderByIdDesc(
                        any(Pageable.class)
                );
    }

    @Test
    @DisplayName("캐시 Miss 후 Redis에 페이지 캐시 키가 생성된다")
    void get_problems_creates_redis_key_after_cache_miss() {
        // given
        int page = 0;
        int size = 20;

        String expectedKey =
                CACHE_NAME + "::page:0:size:20";

        // when
        problemService.getProblems(page, size);

        // then
        assertThat(redisTemplate.hasKey(expectedKey))
                .isTrue();
    }

    @Test
    @DisplayName("서로 다른 캐시 대상 페이지는 각각 Repository를 조회한다")
    void get_problems_caches_different_pages_separately() {
        // given
        String firstPageKey =
                CACHE_NAME + "::page:0:size:20";

        String secondPageKey =
                CACHE_NAME + "::page:1:size:20";

        // when
        problemService.getProblems(0, 20);
        problemService.getProblems(1, 20);

        // then
        verify(
                problemRepository,
                times(2)
        )
                .findAllWithCreatorOrderByIdDesc(
                        any(Pageable.class)
                );

        assertThat(redisTemplate.hasKey(firstPageKey))
                .isTrue();

        assertThat(redisTemplate.hasKey(secondPageKey))
                .isTrue();
    }

    @Test
    @DisplayName("3페이지를 초과한 페이지는 캐시하지 않는다")
    void get_problems_does_not_cache_page_greater_than_two() {
        // given
        int page = 3;
        int size = 20;

        String key =
                CACHE_NAME + "::page:3:size:20";

        // when
        problemService.getProblems(page, size);
        problemService.getProblems(page, size);

        // then
        verify(
                problemRepository,
                times(2)
        )
                .findAllWithCreatorOrderByIdDesc(
                        any(Pageable.class)
                );

        assertThat(redisTemplate.hasKey(key))
                .isFalse();
    }

    @Test
    @DisplayName("페이지 크기가 20이 아니면 캐시하지 않는다")
    void get_problems_does_not_cache_when_size_is_not_twenty() {
        // given
        int page = 0;
        int size = 10;

        String key =
                CACHE_NAME + "::page:0:size:10";

        // when
        problemService.getProblems(page, size);
        problemService.getProblems(page, size);

        // then
        verify(
                problemRepository,
                times(2)
        )
                .findAllWithCreatorOrderByIdDesc(
                        any(Pageable.class)
                );

        assertThat(redisTemplate.hasKey(key))
                .isFalse();
    }

    @Test
    @DisplayName("Redis 캐시에 TTL이 설정된다")
    void get_problems_sets_ttl_on_redis_cache() {
        // given
        String key =
                CACHE_NAME + "::page:0:size:20";

        // when
        problemService.getProblems(0, 20);

        Long ttl = redisTemplate.getExpire(
                key,
                TimeUnit.SECONDS
        );

        // then
        assertThat(ttl)
                .isNotNull()
                .isPositive()
                .isLessThanOrEqualTo(600);
    }

    @Test
    @DisplayName("캐시 대상인 0, 1, 2페이지에만 Redis 키가 생성된다")
    void get_problems_creates_keys_only_for_cache_target_pages() {
        // given
        String firstPageKey =
                CACHE_NAME + "::page:0:size:20";

        String secondPageKey =
                CACHE_NAME + "::page:1:size:20";

        String thirdPageKey =
                CACHE_NAME + "::page:2:size:20";

        // when
        problemService.getProblems(0, 20);
        problemService.getProblems(1, 20);
        problemService.getProblems(2, 20);

        problemService.getProblems(3, 20);
        problemService.getProblems(0, 10);

        Set<String> keys =
                redisTemplate.keys(CACHE_NAME + "::*");

        // then
        assertThat(keys)
                .containsExactlyInAnyOrder(
                        firstPageKey,
                        secondPageKey,
                        thirdPageKey
                );
    }

    @Test
    @DisplayName("문제를 수정하면 문제 목록 캐시가 삭제된다")
    void update_problem_evicts_problem_page_cache() {
        // given
        Long creatorId = 1L;
        Long problemId = 100L;

        User creator = new User(
                "creator",
                "encoded-password"
        );

        ReflectionTestUtils.setField(
                creator,
                "id",
                creatorId
        );

        Problem problem = new Problem(
                "old problem",
                "old description",
                List.of(new Position(3, 3)),
                List.of(new Position(4, 4)),
                PlayerColor.BLACK,
                new Position(10, 10),
                creator
        );

        ReflectionTestUtils.setField(
                problem,
                "id",
                problemId
        );

        ProblemUpdateRequest request =
                new ProblemUpdateRequest(
                        "updated problem",
                        "updated description",
                        List.of(new Position(5, 5)),
                        List.of(new Position(6, 6)),
                        PlayerColor.WHITE,
                        new Position(11, 11)
                );

        given(
                problemRepository.findByIdWithCreator(problemId)
        )
                .willReturn(Optional.of(problem));

        problemService.getProblems(0, 20);
        problemService.getProblems(1, 20);

        assertThat(redisTemplate.keys(CACHE_NAME + "::*"))
                .hasSize(2);

        // when
        problemService.updateProblem(
                creatorId,
                problemId,
                request
        );

        // then
        assertThat(redisTemplate.keys(CACHE_NAME + "::*"))
                .isEmpty();

        assertThat(problem.getTitle())
                .isEqualTo("updated problem");

        assertThat(problem.getDescription())
                .isEqualTo("updated description");
    }

    @Test
    @DisplayName("문제를 삭제하면 문제 목록 캐시가 삭제된다")
    void delete_problem_evicts_problem_page_cache() {
        // given
        Long creatorId = 1L;
        Long problemId = 100L;

        User creator = new User(
                "creator",
                "encoded-password"
        );

        ReflectionTestUtils.setField(
                creator,
                "id",
                creatorId
        );

        Problem problem = new Problem(
                "problem",
                "description",
                List.of(new Position(3, 3)),
                List.of(new Position(4, 4)),
                PlayerColor.BLACK,
                new Position(10, 10),
                creator
        );

        ReflectionTestUtils.setField(
                problem,
                "id",
                problemId
        );

        given(
                problemRepository.findByIdWithCreator(problemId)
        )
                .willReturn(Optional.of(problem));

        problemService.getProblems(0, 20);
        problemService.getProblems(1, 20);
        problemService.getProblems(2, 20);

        assertThat(redisTemplate.keys(CACHE_NAME + "::*"))
                .hasSize(3);

        // when
        problemService.deleteProblem(
                creatorId,
                problemId
        );

        // then
        assertThat(redisTemplate.keys(CACHE_NAME + "::*"))
                .isEmpty();

        verify(attemptRepository)
                .deleteAllByProblemId(problemId);

        verify(problemRepository)
                .deleteById(problemId);
    }
}