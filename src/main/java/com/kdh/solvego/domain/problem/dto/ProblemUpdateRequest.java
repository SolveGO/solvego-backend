package com.kdh.solvego.domain.problem.dto;

import com.kdh.solvego.domain.common.vo.Position;
import com.kdh.solvego.domain.problem.entity.PlayerColor;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@Schema(description = "문제 수정 요청")
public record ProblemUpdateRequest(

        @Schema(
                description = "문제 제목",
                example = "수정된 화점 사활 문제"
        )
        @NotBlank
        String title,

        @Schema(
                description = "문제 설명",
                example = "흑을 살리는 수를 찾아보세요."
        )
        @NotBlank
        String description,

        @Schema(description = "흑돌 위치 목록")
        @NotNull
        @Valid
        List<Position> blackStones,

        @Schema(description = "백돌 위치 목록")
        @NotNull
        @Valid
        List<Position> whiteStones,

        @Schema(
                description = "다음 차례의 돌 색상",
                example = "BLACK"
        )
        @NotNull
        PlayerColor nextPlayer,

        @Schema(description = "정답 위치")
        @NotNull
        @Valid
        Position answerPosition
) {
}