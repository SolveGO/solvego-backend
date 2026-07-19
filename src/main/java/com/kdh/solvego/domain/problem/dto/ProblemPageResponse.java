package com.kdh.solvego.domain.problem.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "페이지네이션된 문제 목록 조회 응답")
public record ProblemPageResponse(

        @Schema(description = "현재 페이지의 문제 목록")
        List<ProblemSummaryResponse> problems,

        @Schema(description = "현재 페이지 번호, 0부터 시작", example = "0")
        int page,

        @Schema(description = "페이지당 문제 수", example = "20")
        int size,

        @Schema(description = "전체 문제 수", example = "10001")
        long totalElements,

        @Schema(description = "전체 페이지 수", example = "501")
        int totalPages
) {
}