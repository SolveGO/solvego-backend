package com.kdh.solvego.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdh.solvego.domain.auth.dto.LoginRequest;
import com.kdh.solvego.domain.attempt.dto.AttemptCreateRequest;
import com.kdh.solvego.domain.common.vo.Position;
import com.kdh.solvego.domain.user.dto.SignupRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MvpApiFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("MVP 전체 흐름: 회원가입부터 오답 조회까지 성공한다")
    void mvp_api_flow_success() throws Exception {
        // given
        String username = "flow" + System.nanoTime() % 1_000_000;
        String password = "1234";

        // 1. 회원가입
        signup(username, password);

        // 2. 로그인
        String accessToken = login(username, password);

        // 3. 문제 등록
        Long problemId = createProblem(accessToken);

        // 4. 문제 목록 조회
        getProblemList(problemId);

        // 5. 문제 상세 조회
        getProblemDetail(problemId);

        // 6. 오답 제출
        submitWrongAttempt(accessToken, problemId);

        // 7. 오답 문제 조회
        getWrongProblems(accessToken, problemId);
    }

    private void signup(String username, String password) throws Exception {
        SignupRequest request = new SignupRequest(username, password);

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").exists());
    }

    private String login(String username, String password) throws Exception {
        LoginRequest request = new LoginRequest(username, password);

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.accessToken").exists())
                .andReturn();

        String responseBody = result.getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        JsonNode jsonNode = objectMapper.readTree(responseBody);
        String accessToken = jsonNode.get("accessToken").asText();

        assertThat(accessToken).isNotBlank();

        return accessToken;
    }

    private Long createProblem(String accessToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/problems")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(problemCreateRequestJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.problemId").exists())
                .andReturn();

        String responseBody = result.getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        JsonNode jsonNode = objectMapper.readTree(responseBody);

        return jsonNode.get("problemId").asLong();
    }

    private void getProblemList(Long problemId) throws Exception {
        mockMvc.perform(get("/api/problems"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.problems").isArray())
                .andExpect(jsonPath("$.problems[0].problemId").value(problemId))
                .andExpect(jsonPath("$.problems[0].title").value("problem title"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    private void getProblemDetail(Long problemId) throws Exception {
        mockMvc.perform(get("/api/problems/{problemId}", problemId))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.title").value("problem title"))
                .andExpect(jsonPath("$.description").value("problem description"))
                .andExpect(jsonPath("$.answerPosition").doesNotExist());
    }

    private void submitWrongAttempt(String accessToken, Long problemId) throws Exception {
        AttemptCreateRequest request =
                new AttemptCreateRequest(new Position(1, 1));

        mockMvc.perform(post("/api/problems/{problemId}/attempts", problemId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.isCorrect").value(false));
    }

    private void getWrongProblems(String accessToken, Long problemId) throws Exception {
        mockMvc.perform(get("/api/users/me/wrong-problems")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].problemId").value(problemId))
                .andExpect(jsonPath("$[0].title").value("problem title"));
    }

    private String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    private String problemCreateRequestJson() {
        return """
                {
                  "title": "problem title",
                  "description": "problem description",
                  "blackStones": [
                    { "x": 3, "y": 3 }
                  ],
                  "whiteStones": [
                    { "x": 4, "y": 4 }
                  ],
                  "nextPlayer": "BLACK",
                  "answerPosition": {
                    "x": 10,
                    "y": 10
                  }
                }
                """;
    }
}