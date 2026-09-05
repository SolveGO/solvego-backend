package com.kdh.solvego.domain.ai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdh.solvego.domain.ai.dto.AiAnalyzeRequest;
import com.kdh.solvego.domain.ai.dto.AiAnalyzeResponse;
import com.kdh.solvego.domain.ai.dto.AiRecommendRequest;
import com.kdh.solvego.domain.ai.dto.AiRecommendResponse;
import com.kdh.solvego.domain.ai.exception.AiServerException;
import com.kdh.solvego.domain.ai.exception.AiTimeoutException;
import com.kdh.solvego.domain.ai.service.AiService;
import com.kdh.solvego.domain.common.vo.Position;
import com.kdh.solvego.domain.problem.entity.PlayerColor;
import com.kdh.solvego.global.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.kdh.solvego.domain.ai.dto.AiStatusResponse;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@WebMvcTest(AiController.class)
@AutoConfigureMockMvc(addFilters = false)
class AiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AiService aiService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    @DisplayName("AI 추천 요청에 성공하면 200 OK와 추천 결과를 반환한다")
    void recommend_success() throws Exception {
        // given
        AiRecommendRequest request = new AiRecommendRequest(
                List.of(),
                List.of(),
                PlayerColor.BLACK
        );

        AiRecommendResponse response = new AiRecommendResponse(
                new Position(15, 3),
                0.48
        );

        when(aiService.recommend(any(AiRecommendRequest.class)))
                .thenReturn(response);

        // when & then
        mockMvc.perform(post("/api/ai/recommend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.bestMove.x").value(15))
                .andExpect(jsonPath("$.bestMove.y").value(3))
                .andExpect(jsonPath("$.bestWinRate").value(0.48));

        verify(aiService).recommend(any(AiRecommendRequest.class));
    }

    @Test
    @DisplayName("AI 분석 요청에 성공하면 200 OK와 분석 결과를 반환한다")
    void analyze_success() throws Exception {
        // given
        AiAnalyzeRequest request = new AiAnalyzeRequest(
                List.of(),
                List.of(),
                PlayerColor.BLACK,
                new Position(10, 10)
        );

        AiAnalyzeResponse response = new AiAnalyzeResponse(
                new Position(15, 3),
                new Position(10, 10),
                0.48,
                0.41,
                0.07
        );

        when(aiService.analyze(any(AiAnalyzeRequest.class)))
                .thenReturn(response);

        // when & then
        mockMvc.perform(post("/api/ai/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.bestMove.x").value(15))
                .andExpect(jsonPath("$.bestMove.y").value(3))
                .andExpect(jsonPath("$.selectedMove.x").value(10))
                .andExpect(jsonPath("$.selectedMove.y").value(10))
                .andExpect(jsonPath("$.bestWinRate").value(0.48))
                .andExpect(jsonPath("$.selectedWinRate").value(0.41))
                .andExpect(jsonPath("$.winRateLoss").value(0.07));

        verify(aiService).analyze(any(AiAnalyzeRequest.class));
    }

    @Test
    @DisplayName("AI 추천 요청값이 올바르지 않으면 400 Bad Request를 반환한다")
    void recommend_fails_when_request_is_invalid() throws Exception {
        // given
        String invalidRequestBody = """
                {
                  "blackStones": [
                    {
                      "x": -1,
                      "y": 3
                    }
                  ],
                  "whiteStones": [],
                  "nextPlayer": "BLACK"
                }
                """;

        // when & then
        mockMvc.perform(post("/api/ai/recommend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequestBody))
                .andExpect(status().isBadRequest());

        verify(aiService, never())
                .recommend(any(AiRecommendRequest.class));
    }

    @Test
    @DisplayName("AI 분석 요청값이 올바르지 않으면 400 Bad Request를 반환한다")
    void analyze_fails_when_request_is_invalid() throws Exception {
        // given
        String invalidRequestBody = """
                {
                  "blackStones": [],
                  "whiteStones": [],
                  "nextPlayer": "BLACK",
                  "selectedPosition": {
                    "x": 19,
                    "y": 10
                  }
                }
                """;

        // when & then
        mockMvc.perform(post("/api/ai/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequestBody))
                .andExpect(status().isBadRequest());

        verify(aiService, never())
                .analyze(any(AiAnalyzeRequest.class));
    }

    @Test
    @DisplayName("AI 추천 서버 통신에 실패하면 502 Bad Gateway를 반환한다")
    void recommend_fails_when_ai_server_error_occurs() throws Exception {
        // given
        AiRecommendRequest request = new AiRecommendRequest(
                List.of(),
                List.of(),
                PlayerColor.BLACK
        );

        when(aiService.recommend(any(AiRecommendRequest.class)))
                .thenThrow(new AiServerException());

        // when & then
        mockMvc.perform(post("/api/ai/recommend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message")
                        .value("Failed to communicate with AI server"));

        verify(aiService).recommend(any(AiRecommendRequest.class));
    }

    @Test
    @DisplayName("AI 분석 서버 통신에 실패하면 502 Bad Gateway를 반환한다")
    void analyze_fails_when_ai_server_error_occurs() throws Exception {
        // given
        AiAnalyzeRequest request = new AiAnalyzeRequest(
                List.of(),
                List.of(),
                PlayerColor.BLACK,
                new Position(10, 10)
        );

        when(aiService.analyze(any(AiAnalyzeRequest.class)))
                .thenThrow(new AiServerException());

        // when & then
        mockMvc.perform(post("/api/ai/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message")
                        .value("Failed to communicate with AI server"));

        verify(aiService).analyze(any(AiAnalyzeRequest.class));
    }

    @Test
    @DisplayName("AI 추천 분석 시간이 초과되면 504 Gateway Timeout을 반환한다")
    void recommend_fails_when_ai_server_times_out() throws Exception {
        // given
        AiRecommendRequest request = new AiRecommendRequest(
                List.of(),
                List.of(),
                PlayerColor.BLACK
        );

        when(aiService.recommend(any(AiRecommendRequest.class)))
                .thenThrow(new AiTimeoutException());

        // when & then
        mockMvc.perform(post("/api/ai/recommend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.message")
                        .value("AI analysis timed out"));

        verify(aiService).recommend(any(AiRecommendRequest.class));
    }

    @Test
    @DisplayName("AI 착수 분석 시간이 초과되면 504 Gateway Timeout을 반환한다")
    void analyze_fails_when_ai_server_times_out() throws Exception {
        // given
        AiAnalyzeRequest request = new AiAnalyzeRequest(
                List.of(),
                List.of(),
                PlayerColor.BLACK,
                new Position(10, 10)
        );

        when(aiService.analyze(any(AiAnalyzeRequest.class)))
                .thenThrow(new AiTimeoutException());

        // when & then
        mockMvc.perform(post("/api/ai/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.message")
                        .value("AI analysis timed out"));

        verify(aiService).analyze(any(AiAnalyzeRequest.class));
    }
    @Test
    @DisplayName("AI 서버 상태 조회에 성공하면 200 OK와 상태를 반환한다")
    void status_success() throws Exception {
        // given
        AiStatusResponse response = new AiStatusResponse("ONLINE");

        when(aiService.status())
                .thenReturn(response);

        // when & then
        mockMvc.perform(get("/api/ai/status"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("ONLINE"));

        verify(aiService).status();
    }
}