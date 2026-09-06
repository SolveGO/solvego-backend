package com.kdh.solvego.domain.ai.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdh.solvego.domain.ai.dto.AiAnalyzeRequest;
import com.kdh.solvego.domain.ai.dto.AiRecommendRequest;
import com.kdh.solvego.domain.auth.dto.LoginRequest;
import com.kdh.solvego.domain.common.vo.Position;
import com.kdh.solvego.domain.problem.entity.PlayerColor;
import com.kdh.solvego.domain.user.dto.SignupRequest;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import com.kdh.solvego.domain.ai.dto.AiGameNextMoveRequest;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AiControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static HttpServer aiServer;

    private static int recommendStatus;
    private static String recommendBody;

    private static int analyzeStatus;
    private static String analyzeBody;

    private static int healthStatus;
    private static String healthBody;

    private static int gameNextMoveStatus;
    private static String gameNextMoveBody;

    @BeforeAll
    static void startAiServer() throws IOException {
        aiServer = HttpServer.create(
                new InetSocketAddress(0),
                0
        );

        aiServer.createContext("/recommend", exchange -> {
            respond(
                    exchange,
                    recommendStatus,
                    recommendBody
            );
        });

        aiServer.createContext("/analyze", exchange -> {
            respond(
                    exchange,
                    analyzeStatus,
                    analyzeBody
            );
        });

        aiServer.createContext("/health", exchange -> {
            respond(
                    exchange,
                    healthStatus,
                    healthBody
            );
        });
        aiServer.createContext("/game/next-move", exchange -> {
            respond(
                    exchange,
                    gameNextMoveStatus,
                    gameNextMoveBody
            );
        });
        aiServer.start();
    }

    @AfterAll
    static void stopAiServer() {
        aiServer.stop(0);
    }

    @DynamicPropertySource
    static void configureAiBaseUrl(
            DynamicPropertyRegistry registry
    ) {
        registry.add(
                "ai.base-url",
                () -> "http://localhost:" + aiServer.getAddress().getPort()
        );
    }

    @BeforeEach
    void setUp() {
        recommendStatus = 200;
        recommendBody = """
                {
                  "bestMove": {
                    "x": 15,
                    "y": 3
                  },
                  "bestWinRate": 0.48
                }
                """;

        analyzeStatus = 200;
        analyzeBody = """
                {
                  "bestMove": {
                    "x": 15,
                    "y": 3
                  },
                  "selectedMove": {
                    "x": 10,
                    "y": 10
                  },
                  "bestWinRate": 0.48,
                  "selectedWinRate": 0.41,
                  "winRateLoss": 0.07
                }
                """;

        healthStatus = 200;
        healthBody = """
                {
                  "status": "ok"
                }
                """;
        gameNextMoveStatus = 200;
        gameNextMoveBody = """
        {
          "move": {
            "x": 4,
            "y": 3
          },
          "winRate": 0.99,
          "scoreLead": 13.05
        }
        """;
    }

    @Test
    @DisplayName("JWT로 인증된 사용자는 AI 추천 결과를 조회할 수 있다")
    void recommend_success() throws Exception {
        // given
        String accessToken =
                signupAndLogin("aiuser1", "1234");

        AiRecommendRequest request =
                new AiRecommendRequest(
                        List.of(),
                        List.of(),
                        PlayerColor.BLACK
                );

        // when & then
        mockMvc.perform(post("/api/ai/recommend")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                bearer(accessToken)
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                objectMapper.writeValueAsString(request)
                        ))
                .andExpect(status().isOk())
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_JSON
                        )
                )
                .andExpect(
                        jsonPath("$.bestMove.x").value(15)
                )
                .andExpect(
                        jsonPath("$.bestMove.y").value(3)
                )
                .andExpect(
                        jsonPath("$.bestWinRate").value(0.48)
                );
    }

    @Test
    @DisplayName("JWT로 인증된 사용자는 선택한 착수를 AI로 분석할 수 있다")
    void analyze_success() throws Exception {
        // given
        String accessToken =
                signupAndLogin("aiuser2", "1234");

        AiAnalyzeRequest request =
                new AiAnalyzeRequest(
                        List.of(),
                        List.of(),
                        PlayerColor.BLACK,
                        new Position(10, 10)
                );

        // when & then
        mockMvc.perform(post("/api/ai/analyze")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                bearer(accessToken)
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                objectMapper.writeValueAsString(request)
                        ))
                .andExpect(status().isOk())
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_JSON
                        )
                )
                .andExpect(
                        jsonPath("$.bestMove.x").value(15)
                )
                .andExpect(
                        jsonPath("$.bestMove.y").value(3)
                )
                .andExpect(
                        jsonPath("$.selectedMove.x").value(10)
                )
                .andExpect(
                        jsonPath("$.selectedMove.y").value(10)
                )
                .andExpect(
                        jsonPath("$.bestWinRate").value(0.48)
                )
                .andExpect(
                        jsonPath("$.selectedWinRate").value(0.41)
                )
                .andExpect(
                        jsonPath("$.winRateLoss").value(0.07)
                );
    }

    @Test
    @DisplayName("JWT 없이 AI 추천을 요청하면 401 Unauthorized를 반환한다")
    void recommend_fails_without_jwt() throws Exception {
        // given
        AiRecommendRequest request =
                new AiRecommendRequest(
                        List.of(),
                        List.of(),
                        PlayerColor.BLACK
                );

        // when & then
        mockMvc.perform(post("/api/ai/recommend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                objectMapper.writeValueAsString(request)
                        ))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("AI 추천 서버가 오류를 반환하면 502 Bad Gateway를 반환한다")
    void recommend_fails_when_ai_server_returns_error()
            throws Exception {

        // given
        String accessToken =
                signupAndLogin("aiuser3", "1234");

        recommendStatus = 500;
        recommendBody = """
                {
                  "detail": "internal server error"
                }
                """;

        AiRecommendRequest request =
                new AiRecommendRequest(
                        List.of(),
                        List.of(),
                        PlayerColor.BLACK
                );

        // when & then
        mockMvc.perform(post("/api/ai/recommend")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                bearer(accessToken)
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                objectMapper.writeValueAsString(request)
                        ))
                .andExpect(status().isBadGateway())
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "Failed to communicate with AI server"
                                )
                );
    }

    @Test
    @DisplayName("AI 분석 시간이 초과되면 504 Gateway Timeout을 반환한다")
    void analyze_fails_when_ai_server_times_out()
            throws Exception {

        // given
        String accessToken =
                signupAndLogin("aiuser4", "1234");

        analyzeStatus = 504;
        analyzeBody = """
                {
                  "detail": "analysis timed out"
                }
                """;

        AiAnalyzeRequest request =
                new AiAnalyzeRequest(
                        List.of(),
                        List.of(),
                        PlayerColor.BLACK,
                        new Position(10, 10)
                );

        // when & then
        mockMvc.perform(post("/api/ai/analyze")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                bearer(accessToken)
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                objectMapper.writeValueAsString(request)
                        ))
                .andExpect(status().isGatewayTimeout())
                .andExpect(
                        jsonPath("$.message")
                                .value("AI analysis timed out")
                );
    }

    @Test
    @DisplayName("AI 서버가 정상이면 인증 없이 ONLINE 상태를 조회할 수 있다")
    void status_returns_online_when_ai_server_is_healthy() throws Exception {
        // when & then
        mockMvc.perform(get("/api/ai/status"))
                .andExpect(status().isOk())
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_JSON
                        )
                )
                .andExpect(
                        jsonPath("$.status").value("ONLINE")
                );
    }

    @Test
    @DisplayName("AI 서버가 오류를 반환하면 인증 없이 OFFLINE 상태를 조회할 수 있다")
    void status_returns_offline_when_ai_server_returns_error() throws Exception {
        // given
        healthStatus = 500;
        healthBody = """
                {
                  "detail": "internal server error"
                }
                """;

        // when & then
        mockMvc.perform(get("/api/ai/status"))
                .andExpect(status().isOk())
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_JSON
                        )
                )
                .andExpect(
                        jsonPath("$.status").value("OFFLINE")
                );
    }

    @Test
    @DisplayName("JWT로 인증된 사용자는 AI 대국 다음 수를 조회할 수 있다")
    void gameNextMove_success() throws Exception {
        // given
        String accessToken =
                signupAndLogin("aiuser5", "1234");

        AiGameNextMoveRequest request =
                new AiGameNextMoveRequest(
                        List.of(
                                new AiGameNextMoveRequest.Move(
                                        AiGameNextMoveRequest.Player.BLACK,
                                        new Position(3, 15)
                                ),
                                new AiGameNextMoveRequest.Move(
                                        AiGameNextMoveRequest.Player.WHITE,
                                        new Position(15, 3)
                                ),
                                new AiGameNextMoveRequest.Move(
                                        AiGameNextMoveRequest.Player.BLACK,
                                        null
                                )
                        )
                );

        // when & then
        mockMvc.perform(post("/api/ai/game/next-move")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                bearer(accessToken)
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                objectMapper.writeValueAsString(request)
                        ))
                .andExpect(status().isOk())
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_JSON
                        )
                )
                .andExpect(
                        jsonPath("$.move.x").value(4)
                )
                .andExpect(
                        jsonPath("$.move.y").value(3)
                )
                .andExpect(
                        jsonPath("$.winRate").value(0.99)
                )
                .andExpect(
                        jsonPath("$.scoreLead").value(13.05)
                );
    }
    @Test
    @DisplayName("AI 대국에서 AI가 PASS하면 move를 null로 반환한다")
    void gameNextMove_success_when_ai_passes() throws Exception {
        // given
        String accessToken =
                signupAndLogin("aiuser6", "1234");

        gameNextMoveBody = """
            {
              "move": null,
              "winRate": 0.82,
              "scoreLead": 10.5
            }
            """;

        AiGameNextMoveRequest request =
                new AiGameNextMoveRequest(
                        List.of(
                                new AiGameNextMoveRequest.Move(
                                        AiGameNextMoveRequest.Player.BLACK,
                                        new Position(3, 15)
                                )
                        )
                );

        // when & then
        mockMvc.perform(post("/api/ai/game/next-move")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                bearer(accessToken)
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                objectMapper.writeValueAsString(request)
                        ))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.move").value((Object) null)
                )
                .andExpect(
                        jsonPath("$.winRate").value(0.82)
                )
                .andExpect(
                        jsonPath("$.scoreLead").value(10.5)
                );
    }
    @Test
    @DisplayName("JWT 없이 AI 대국 다음 수를 요청하면 401 Unauthorized를 반환한다")
    void gameNextMove_fails_without_jwt() throws Exception {
        // given
        AiGameNextMoveRequest request =
                new AiGameNextMoveRequest(List.of());

        // when & then
        mockMvc.perform(post("/api/ai/game/next-move")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                objectMapper.writeValueAsString(request)
                        ))
                .andExpect(status().isUnauthorized());
    }

    private String signupAndLogin(
            String username,
            String password
    ) throws Exception {

        SignupRequest signupRequest =
                new SignupRequest(username, password);

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                objectMapper.writeValueAsString(
                                        signupRequest
                                )
                        ))
                .andExpect(status().isCreated());

        LoginRequest loginRequest =
                new LoginRequest(username, password);

        MvcResult result =
                mockMvc.perform(post("/api/auth/login")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        objectMapper.writeValueAsString(
                                                loginRequest
                                        )
                                ))
                        .andExpect(status().isOk())
                        .andExpect(
                                jsonPath("$.accessToken").exists()
                        )
                        .andReturn();

        String responseBody =
                result.getResponse()
                        .getContentAsString(
                                StandardCharsets.UTF_8
                        );

        JsonNode jsonNode =
                objectMapper.readTree(responseBody);

        String accessToken =
                jsonNode.get("accessToken").asText();

        assertThat(accessToken).isNotBlank();

        return accessToken;
    }

    private String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    private static void respond(
            HttpExchange exchange,
            int status,
            String body
    ) throws IOException {

        byte[] response =
                body.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().add(
                "Content-Type",
                "application/json"
        );

        exchange.sendResponseHeaders(
                status,
                response.length
        );

        try (OutputStream outputStream =
                     exchange.getResponseBody()) {
            outputStream.write(response);
        }
    }
}