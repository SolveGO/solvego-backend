package com.kdh.solvego.domain.attempt.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdh.solvego.domain.attempt.dto.AttemptCreateRequest;
import com.kdh.solvego.domain.auth.dto.LoginRequest;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AttemptControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("JWT로 인증된 사용자는 정답 좌표를 제출하면 201 Created와 true를 반환받는다")
    void create_attempt_success_when_answer_is_correct() throws Exception {
        // given
        String accessToken = signupAndLogin("attemptuser1", "1234");
        Long problemId = createProblem(accessToken);

        AttemptCreateRequest request =
                new AttemptCreateRequest(new Position(10, 10));

        // when & then
        mockMvc.perform(post("/api/problems/{problemId}/attempts", problemId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.isCorrect").value(true));
    }

    @Test
    @DisplayName("JWT로 인증된 사용자는 오답 좌표를 제출하면 201 Created와 false를 반환받는다")
    void create_attempt_success_when_answer_is_wrong() throws Exception {
        // given
        String accessToken = signupAndLogin("attemptuser2", "1234");
        Long problemId = createProblem(accessToken);

        AttemptCreateRequest request =
                new AttemptCreateRequest(new Position(1, 1));

        // when & then
        mockMvc.perform(post("/api/problems/{problemId}/attempts", problemId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.isCorrect").value(false));
    }

    @Test
    @DisplayName("JWT 없이 풀이를 제출하면 401 Unauthorized를 반환한다")
    void create_attempt_fails_without_jwt() throws Exception {
        // given
        String accessToken = signupAndLogin("attemptuser3", "1234");
        Long problemId = createProblem(accessToken);

        AttemptCreateRequest request =
                new AttemptCreateRequest(new Position(10, 10));

        // when & then
        mockMvc.perform(post("/api/problems/{problemId}/attempts", problemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("풀이 제출 요청값이 올바르지 않으면 400 Bad Request를 반환한다")
    void create_attempt_fails_when_request_is_invalid() throws Exception {
        // given
        String accessToken = signupAndLogin("attemptuser4", "1234");
        Long problemId = createProblem(accessToken);

        String invalidRequestBody = """
                {
                  "selectedPosition": {
                    "x": -1,
                    "y": 10
                  }
                }
                """;

        // when & then
        mockMvc.perform(post("/api/problems/{problemId}/attempts", problemId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("존재하지 않는 문제에 풀이를 제출하면 404 Not Found를 반환한다")
    void create_attempt_fails_when_problem_not_found() throws Exception {
        // given
        String accessToken = signupAndLogin("attemptuser5", "1234");
        Long notFoundProblemId = 999999L;

        AttemptCreateRequest request =
                new AttemptCreateRequest(new Position(10, 10));

        // when & then
        mockMvc.perform(post("/api/problems/{problemId}/attempts", notFoundProblemId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    private String signupAndLogin(String username, String password) throws Exception {
        SignupRequest signupRequest = new SignupRequest(username, password);

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signupRequest)))
                .andExpect(status().isCreated());

        LoginRequest loginRequest = new LoginRequest(username, password);

        MvcResult result = mockMvc.perform(post("/api/auth/login").header("Origin", "http://localhost:5173").header("X-SolveGO-CSRF", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
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