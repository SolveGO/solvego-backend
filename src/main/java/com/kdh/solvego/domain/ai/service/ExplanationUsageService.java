package com.kdh.solvego.domain.ai.service;

import com.kdh.solvego.domain.ai.dto.AiExplanationUsageResponse;
import com.kdh.solvego.domain.ai.repository.ExplanationUsageRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HexFormat;

@Service
public class ExplanationUsageService {
    private final ExplanationUsageRepository repository;
    private final ExplanationUsageLimitPolicy limitPolicy;
    private final Clock clock;
    private final ZoneId zoneId;

    @Autowired
    public ExplanationUsageService(
            ExplanationUsageRepository repository,
            ExplanationUsageLimitPolicy limitPolicy,
            @Value("${ai.explanation.time-zone}") String timeZone
    ) {
        this(repository, limitPolicy, Clock.systemUTC(), ZoneId.of(timeZone));
    }

    ExplanationUsageService(
            ExplanationUsageRepository repository,
            ExplanationUsageLimitPolicy limitPolicy,
            Clock clock,
            ZoneId zoneId
    ) {
        this.repository = repository;
        this.limitPolicy = limitPolicy;
        this.clock = clock;
        this.zoneId = zoneId;
    }

    public AiExplanationUsageResponse getUsage(Long userId) {
        UsageWindow window = currentWindow();
        int dailyLimit = limitPolicy.dailyLimitFor(userId);
        int usedCount = repository.count(userId, window.date());
        return response(usedCount, Math.max(0, dailyLimit - usedCount), dailyLimit, window);
    }

    public AiExplanationUsageResponse consume(Long userId, String evidenceToken) {
        UsageWindow window = currentWindow();
        int dailyLimit = limitPolicy.dailyLimitFor(userId);
        ExplanationUsageRepository.UsageCount count = repository.consume(
                userId,
                window.date(),
                hash(evidenceToken),
                dailyLimit,
                window.resetsAt()
        );
        return response(
                count.usedCount(),
                count.remainingCount(),
                dailyLimit,
                window
        );
    }

    private UsageWindow currentWindow() {
        ZonedDateTime now = ZonedDateTime.now(clock).withZoneSameInstant(zoneId);
        return new UsageWindow(
                now.toLocalDate(),
                now.toLocalDate().plusDays(1).atStartOfDay(zoneId).toInstant()
        );
    }

    private AiExplanationUsageResponse response(
            int usedCount,
            int remainingCount,
            int dailyLimit,
            UsageWindow window
    ) {
        return new AiExplanationUsageResponse(
                usedCount,
                Math.max(0, remainingCount),
                dailyLimit,
                window.resetsAt()
        );
    }

    private String hash(String evidenceToken) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(
                            evidenceToken.getBytes(StandardCharsets.UTF_8)
                    )
            );
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    private record UsageWindow(LocalDate date, Instant resetsAt) {
    }
}
