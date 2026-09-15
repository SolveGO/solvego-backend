package com.kdh.solvego.domain.ai.dto;

import com.kdh.solvego.domain.ai.type.MoveType;
import com.kdh.solvego.domain.common.vo.Position;

import java.util.List;

public record AiGameNextMoveAiResponse(
        MoveType moveType,
        Position move,
        double winRate,
        double scoreLead,
        List<AiGameCandidate> candidates,
        String evidenceToken
) {
    public AiGameNextMoveAiResponse(
            MoveType moveType,
            Position move,
            double winRate,
            double scoreLead
    ) {
        this(moveType, move, winRate, scoreLead, List.of(), "");
    }
}
