package com.kdh.solvego.domain.ai.dto;

import com.kdh.solvego.domain.ai.type.GameEndReason;
import com.kdh.solvego.domain.ai.type.GameResult;
import com.kdh.solvego.domain.ai.type.MoveType;
import com.kdh.solvego.domain.common.vo.Position;

import java.util.List;

public record AiGameNextMoveResponse(
        MoveType moveType,
        Position move,
        double winRate,
        double scoreLead,
        List<AiGameCandidate> candidates,
        String evidenceToken,
        boolean gameEnded,
        GameResult result,
        GameEndReason endReason
) {
    public AiGameNextMoveResponse(
            MoveType moveType,
            Position move,
            double winRate,
            double scoreLead,
            boolean gameEnded,
            GameResult result,
            GameEndReason endReason
    ) {
        this(moveType, move, winRate, scoreLead, List.of(), "",
                gameEnded, result, endReason);
    }
}
