package com.kdh.solvego.domain.ai.dto;

import com.kdh.solvego.domain.common.vo.Position;

import java.util.List;

public record AiGameNextMoveRequest(
        List<Move> moves
) {

    public record Move(
            Player player,
            Position position
    ) {
    }

    public enum Player {
        BLACK,
        WHITE
    }
}