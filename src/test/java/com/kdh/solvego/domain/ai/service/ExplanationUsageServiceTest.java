package com.kdh.solvego.domain.ai.service;

import com.kdh.solvego.domain.ai.dto.AiExplanationUsageResponse;
import com.kdh.solvego.domain.ai.repository.ExplanationUsageRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExplanationUsageServiceTest {
    private final ExplanationUsageRepository repository =
            mock(ExplanationUsageRepository.class);
    private final ExplanationUsageLimitPolicy limitPolicy =
            mock(ExplanationUsageLimitPolicy.class);
    private final ZoneId zoneId = ZoneId.of("Asia/Seoul");

    @Test
    @DisplayName("서울 기준 날짜와 다음 자정으로 사용량을 조회한다")
    void get_usage_uses_configured_server_time_zone() {
        Clock clock = Clock.fixed(
                Instant.parse("2026-09-15T14:30:00Z"),
                ZoneOffset.UTC
        );
        ExplanationUsageService service = new ExplanationUsageService(
                repository, limitPolicy, clock, zoneId
        );
        when(limitPolicy.dailyLimitFor(1L)).thenReturn(5);
        when(repository.count(1L, LocalDate.of(2026, 9, 15))).thenReturn(2);

        AiExplanationUsageResponse response = service.getUsage(1L);

        assertThat(response.usedCount()).isEqualTo(2);
        assertThat(response.remainingCount()).isEqualTo(3);
        assertThat(response.dailyLimit()).isEqualTo(5);
        assertThat(response.resetsAt())
                .isEqualTo(Instant.parse("2026-09-15T15:00:00Z"));
    }

    @Test
    @DisplayName("서울 자정이 지나면 새 날짜 키에 사용량을 기록한다")
    void consume_uses_new_date_after_midnight() {
        Clock clock = Clock.fixed(
                Instant.parse("2026-09-15T15:01:00Z"),
                ZoneOffset.UTC
        );
        ExplanationUsageService service = new ExplanationUsageService(
                repository, limitPolicy, clock, zoneId
        );
        when(limitPolicy.dailyLimitFor(1L)).thenReturn(5);
        when(repository.consume(
                eq(1L),
                eq(LocalDate.of(2026, 9, 16)),
                any(String.class),
                eq(5),
                eq(Instant.parse("2026-09-16T15:00:00Z"))
        )).thenReturn(new ExplanationUsageRepository.UsageCount(1, 4));

        AiExplanationUsageResponse response = service.consume(1L, "evidence-token");

        assertThat(response.usedCount()).isEqualTo(1);
        assertThat(response.remainingCount()).isEqualTo(4);
        verify(repository).consume(
                eq(1L),
                eq(LocalDate.of(2026, 9, 16)),
                any(String.class),
                eq(5),
                eq(Instant.parse("2026-09-16T15:00:00Z"))
        );
    }
}
