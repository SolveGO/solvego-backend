package com.kdh.solvego.domain.ai.dto;

import com.kdh.solvego.domain.common.vo.Position;

public record AiGameNextMoveResponse(
        Position move,
        double winRate,
        double scoreLead
) {
}