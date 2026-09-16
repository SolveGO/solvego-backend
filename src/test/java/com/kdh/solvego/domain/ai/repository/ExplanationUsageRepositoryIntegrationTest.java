package com.kdh.solvego.domain.ai.repository;

import com.kdh.solvego.domain.ai.exception.ExplanationDailyLimitExceededException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class ExplanationUsageRepositoryIntegrationTest {
    private static final Long USER_ID = 987654321L;

    @Autowired
    private ExplanationUsageRepository repository;

    @Autowired
    private StringRedisTemplate redis;

    @AfterEach
    void cleanUp() {
        var keys = redis.keys("solvego:ai:explanation-usage:" + USER_ID + ":*");
        if (keys != null && !keys.isEmpty()) redis.delete(keys);
    }

    @Test
    @DisplayName("같은 evidence는 한 번만 차감하고 서로 다른 evidence는 일일 한도까지 차감한다")
    void consume_is_idempotent_and_enforces_daily_limit() {
        LocalDate date = LocalDate.of(2026, 9, 16);
        Instant resetsAt = Instant.now().plusSeconds(3600);

        assertThat(repository.consume(USER_ID, date, "hash-1", 5, resetsAt))
                .isEqualTo(new ExplanationUsageRepository.UsageCount(1, 4));
        assertThat(repository.consume(USER_ID, date, "hash-1", 5, resetsAt))
                .isEqualTo(new ExplanationUsageRepository.UsageCount(1, 4));
        for (int index = 2; index <= 5; index++) {
            repository.consume(USER_ID, date, "hash-" + index, 5, resetsAt);
        }

        assertThat(repository.count(USER_ID, date)).isEqualTo(5);
        assertThatThrownBy(() -> repository.consume(
                USER_ID, date, "hash-6", 5, resetsAt
        )).isInstanceOf(ExplanationDailyLimitExceededException.class);
    }

    @Test
    @DisplayName("날짜가 바뀌면 별도의 일일 사용량으로 시작한다")
    void usage_is_separated_by_date() {
        Instant resetsAt = Instant.now().plusSeconds(3600);
        LocalDate firstDate = LocalDate.of(2026, 9, 16);
        LocalDate nextDate = firstDate.plusDays(1);

        repository.consume(USER_ID, firstDate, "hash-1", 5, resetsAt);

        assertThat(repository.count(USER_ID, nextDate)).isZero();
        assertThat(repository.consume(USER_ID, nextDate, "hash-2", 5, resetsAt))
                .isEqualTo(new ExplanationUsageRepository.UsageCount(1, 4));
    }
}
