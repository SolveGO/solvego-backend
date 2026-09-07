package com.kdh.solvego.domain.ai.dto;

import com.kdh.solvego.domain.ai.type.GameEndReason;
import com.kdh.solvego.domain.ai.type.GameResult;
import com.kdh.solvego.domain.ai.type.MoveType;
import com.kdh.solvego.domain.common.vo.Position;

public record AiGameNextMoveResponse(
        MoveType moveType,
        Position move,
        double winRate,
        double scoreLead,
        boolean gameEnded,
        GameResult result,
        GameEndReason endReason
) {
}