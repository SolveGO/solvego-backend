package com.kdh.solvego.domain.ai.dto;

import com.kdh.solvego.domain.common.vo.Position;

public record AiAnalyzeResponse(
        Position bestMove,
        Position selectedMove,
        double bestWinRate,
        double selectedWinRate,
        double winRateLoss
) {
}