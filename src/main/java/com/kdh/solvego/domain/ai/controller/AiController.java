package com.kdh.solvego.domain.ai.controller;

import com.kdh.solvego.domain.ai.dto.AiAnalyzeRequest;
import com.kdh.solvego.domain.ai.dto.AiAnalyzeResponse;
import com.kdh.solvego.domain.ai.dto.AiGameNextMoveRequest;
import com.kdh.solvego.domain.ai.dto.AiGameNextMoveResponse;
import com.kdh.solvego.domain.ai.dto.AiExplanationRequest;
import com.kdh.solvego.domain.ai.dto.AiExplanationResponse;
import com.kdh.solvego.domain.ai.dto.AiExplanationUsageResponse;
import com.kdh.solvego.domain.ai.dto.AiRecommendRequest;
import com.kdh.solvego.domain.ai.dto.AiRecommendResponse;
import com.kdh.solvego.domain.ai.dto.AiStatusResponse;
import com.kdh.solvego.domain.ai.service.AiService;
import com.kdh.solvego.global.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "AI", description = "AI 추천 및 착수 분석 관련 API")
@RestController
@RequestMapping(
        value = "/api/ai",
        produces = MediaType.APPLICATION_JSON_VALUE
)
public class AiController {

    private final AiService aiService;

    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    @Operation(
            summary = "AI 추천 수 조회",
            description = "현재 바둑판 상태를 기반으로 AI가 추천하는 최선의 착수와 예상 승률을 반환합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "AI 추천 수 조회 성공"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "요청 형식 오류",
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
                    responseCode = "502",
                    description = "AI 서버 통신 오류",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "504",
                    description = "AI 분석 시간 초과",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping(
            value = "/recommend",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    public AiRecommendResponse recommend(
            @Valid @RequestBody AiRecommendRequest request
    ) {
        return aiService.recommend(request);
    }

    @Operation(
            summary = "사용자 착수 AI 분석",
            description = "사용자가 선택한 착수를 AI의 최선의 착수와 비교하여 승률과 승률 손실을 반환합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "사용자 착수 AI 분석 성공"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "요청 형식 오류",
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
                    responseCode = "502",
                    description = "AI 서버 통신 오류",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "504",
                    description = "AI 분석 시간 초과",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping(
            value = "/analyze",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    public AiAnalyzeResponse analyze(
            @Valid @RequestBody AiAnalyzeRequest request
    ) {
        return aiService.analyze(request);
    }

    @Operation(
            summary = "AI 대국 다음 수 조회",
            description = "현재까지의 대국 착수 기록을 기반으로 AI의 다음 수, 예상 승률, 예상 집 차이를 반환하며, 대국이 종료된 경우 종료 여부와 결과 및 종료 사유를 함께 반환합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "AI 대국 다음 수 조회 성공"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "요청 형식 오류",
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
                    responseCode = "502",
                    description = "AI 서버 통신 오류",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "504",
                    description = "AI 분석 시간 초과",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping(
            value = "/game/next-move",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    public AiGameNextMoveResponse gameNextMove(
            @Valid @RequestBody AiGameNextMoveRequest request
    ) {
        return aiService.gameNextMove(request);
    }

    @Operation(
            summary = "AI 착수 해설",
            description = "해당 AI 턴에서 서명한 KataGo 후보 근거로 해설을 반환합니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping(
            value = "/game/explanation",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    public AiExplanationResponse explain(
            Authentication authentication,
            @Valid @RequestBody AiExplanationRequest request
    ) {
        Long userId = (Long) authentication.getPrincipal();
        return aiService.explain(userId, request);
    }

    @Operation(
            summary = "AI 착수 해설 일일 사용량",
            description = "로그인한 사용자의 서버 기준 일일 해설 사용량을 반환합니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/game/explanation/usage")
    public AiExplanationUsageResponse getExplanationUsage(
            Authentication authentication
    ) {
        Long userId = (Long) authentication.getPrincipal();
        return aiService.getExplanationUsage(userId);
    }

    @Operation(
            summary = "AI 서버 상태 조회",
            description = "AI 서버의 현재 연결 상태를 반환합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "AI 서버 상태 조회 성공"
            )
    })
    @GetMapping("/status")
    public AiStatusResponse status() {
        return aiService.status();
    }
}
