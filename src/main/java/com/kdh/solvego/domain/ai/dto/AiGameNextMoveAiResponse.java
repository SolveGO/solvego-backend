package com.kdh.solvego.domain.ai.dto;

import com.kdh.solvego.domain.ai.type.MoveType;
import com.kdh.solvego.domain.common.vo.Position;

public record AiGameNextMoveAiResponse(
        MoveType moveType,
        Position move,
        double winRate,
        double scoreLead
) {
}