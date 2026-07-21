package com.kdh.solvego.domain.problem.controller;

import com.kdh.solvego.domain.problem.dto.*;
import com.kdh.solvego.domain.problem.service.ProblemService;
import com.kdh.solvego.global.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Problem", description = "문제 관련 API")
@RestController
@RequestMapping(
        value = "/api/problems",
        produces = MediaType.APPLICATION_JSON_VALUE
)
public class ProblemController {

    private final ProblemService problemService;

    public ProblemController(ProblemService problemService) {
        this.problemService = problemService;
    }

    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "문제 등록",
            description = "제목, 설명, 바둑판 상태, 정답 좌표를 입력받아 새로운 문제를 등록합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "문제 등록 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 입력 정보",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증되지 않은 사용자",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ProblemCreateResponse createProblem(
            Authentication authentication,
            @Valid @RequestBody ProblemCreateRequest request
    ) {
        Long userId = (Long) authentication.getPrincipal();

        return problemService.createProblem(userId, request);
    }

    @Operation(
            summary = "문제 목록 조회 (페이지네이션)",
            description = "등록된 문제 목록을 최신순으로 페이지 단위로 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "문제 목록 조회 성공")
    })
    @GetMapping
    public ProblemPageResponse getProblems(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return problemService.getProblems(page, size);
    }


    @Operation(
            summary = "[Legacy] 전체 문제 목록 조회",
            description = "페이지네이션을 적용하지 않고 등록된 모든 문제를 최신순으로 조회합니다. 성능 비교를 위한 임시 API입니다.",
            deprecated = true
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "전체 문제 목록 조회 성공")
    })
    @GetMapping("/legacy")
    public ProblemListResponse getProblemsLegacy() {
        return problemService.getProblems();
    }


    @Operation(
            summary = "문제 상세 조회",
            description = "특정 문제의 제목, 설명, 바둑판 상태를 조회합니다. 정답 좌표는 응답에 포함하지 않습니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "문제 조회 성공"),
            @ApiResponse(
                    responseCode = "404",
                    description = "존재하지 않는 문제",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    @GetMapping("/{problemId}")
    public ProblemDetailResponse getProblem(@PathVariable("problemId") Long problemId) {
        return problemService.getProblem(problemId);
    }

    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "문제 수정",
            description = "인증된 사용자가 자신이 등록한 문제의 전체 내용을 수정합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "문제 수정 성공"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 입력 정보",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증되지 않은 사용자",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "문제 수정 권한 없음",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "존재하지 않는 문제",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    @PutMapping(
            value = "/{problemId}",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateProblem(
            Authentication authentication,
            @PathVariable("problemId") Long problemId,
            @Valid @RequestBody ProblemUpdateRequest request
    ) {
        Long userId = (Long) authentication.getPrincipal();

        problemService.updateProblem(userId, problemId, request);
    }

}
