package com.kdh.solvego.domain.ai.dto;

import java.time.Instant;

public record AiExplanationUsageResponse(
        int usedCount,
        int remainingCount,
        int dailyLimit,
        Instant resetsAt
) {
}
