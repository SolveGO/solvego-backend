package com.kdh.solvego.domain.user.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdh.solvego.domain.user.repository.UserRepository;
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
class UserControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("회원가입에 성공하면 사용자가 저장되고 201 Created를 반환한다")
    void signup_success() throws Exception {
        // given
        String requestBody = """
                {
                  "username": "username1",
                  "password": "1234"
                }
                """;

        // when & then
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").exists());

        assertThat(userRepository.existsByUsername("username1")).isTrue();
    }

    @Test
    @DisplayName("이미 존재하는 username으로 회원가입하면 409 Conflict를 반환한다")
    void signup_fails_when_username_is_duplicated() throws Exception {
        // given
        String requestBody = """
                {
                  "username": "username2",
                  "password": "1234"
                }
                """;

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated());

        // when & then
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("JWT로 인증된 사용자는 자신이 틀린 문제 목록을 조회할 수 있다")
    void get_wrong_problems_success() throws Exception {
        // given
        String signupRequestBody = """
                {
                  "username": "username3",
                  "password": "1234"
                }
                """;

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupRequestBody))
                .andExpect(status().isCreated());

        String loginRequestBody = """
                {
                  "username": "username3",
                  "password": "1234"
                }
                """;

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login").header("Origin", "http://localhost:5173").header("X-SolveGO-CSRF", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andReturn();

        String loginResponseBody = loginResult.getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        JsonNode loginJsonNode = objectMapper.readTree(loginResponseBody);
        String accessToken = loginJsonNode.get("accessToken").asText();

        assertThat(accessToken).isNotBlank();

        String problemCreateRequestBody = """
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

        MvcResult problemCreateResult = mockMvc.perform(post("/api/problems")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(problemCreateRequestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.problemId").exists())
                .andReturn();

        String problemCreateResponseBody = problemCreateResult.getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        JsonNode problemJsonNode = objectMapper.readTree(problemCreateResponseBody);
        Long problemId = problemJsonNode.get("problemId").asLong();

        String wrongAttemptRequestBody = """
                {
                  "selectedPosition": {
                    "x": 1,
                    "y": 1
                  }
                }
                """;

        mockMvc.perform(post("/api/problems/{problemId}/attempts", problemId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(wrongAttemptRequestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isCorrect").value(false));

        // when & then
        mockMvc.perform(get("/api/users/me/wrong-problems")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].problemId").value(problemId))
                .andExpect(jsonPath("$[0].title").value("problem title"));
    }

    @Test
    @DisplayName("JWT 없이 틀린 문제 목록을 조회하면 401 Unauthorized를 반환한다")
    void get_wrong_problems_fails_without_jwt() throws Exception {
        mockMvc.perform(get("/api/users/me/wrong-problems"))
                .andExpect(status().isUnauthorized());
    }
}