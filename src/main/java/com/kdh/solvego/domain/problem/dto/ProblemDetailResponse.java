package com.kdh.solvego.domain.problem.dto;

import com.kdh.solvego.domain.common.vo.Position;
import com.kdh.solvego.domain.problem.entity.PlayerColor;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "문제 상세 조회 응답")
public record ProblemDetailResponse(

        @Schema(description = "문제 ID", example = "1")
        Long problemId,

        @Schema(description = "문제 제목", example = "화점 사활 문제")
        String title,

        @Schema(description = "문제 설명", example = "흑이 살 수 있는 수를 찾으세요.")
        String description,

        @Schema(description = "흑돌 좌표 목록")
        List<Position> blackStones,

        @Schema(description = "백돌 좌표 목록")
        List<Position> whiteStones,

        @Schema(description = "다음 착수자", example = "BLACK")
        PlayerColor nextPlayer,

        @Schema(description = "문제 작성자 이름", example = "solvego123")
        String creatorName,

        @Schema(description = "현재 사용자의 수정/삭제 가능 여부", example = "true")
        boolean owner
) {
}