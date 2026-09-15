package com.kdh.solvego.domain.auth.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdh.solvego.domain.auth.dto.LoginRequest;
import com.kdh.solvego.domain.user.dto.SignupRequest;
import com.kdh.solvego.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("가입된 사용자가 로그인에 성공하면 200 OK와 accessToken을 반환한다")
    void login_success() throws Exception {
        // given
        String username = "username1";
        String password = "1234";

        signup(username, password);

        LoginRequest request = new LoginRequest(username, password);

        // when & then
        MvcResult result = mockMvc.perform(post("/api/auth/login").header("Origin", "http://localhost:5173").header("X-SolveGO-CSRF", "1")
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
    }

    @Test
    @DisplayName("로그인 요청값이 올바르지 않으면 400 Bad Request를 반환한다")
    void login_fails_when_request_is_invalid() throws Exception {
        // given
        LoginRequest request = new LoginRequest("", "1234");

        // when & then
        mockMvc.perform(post("/api/auth/login").header("Origin", "http://localhost:5173").header("X-SolveGO-CSRF", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("아이디 또는 비밀번호가 틀리면 401 Unauthorized를 반환한다")
    void login_fails_when_username_or_password_is_invalid() throws Exception {
        // given
        String username = "username2";
        String password = "1234";

        signup(username, password);

        LoginRequest request = new LoginRequest(username, "wrong-password");

        // when & then
        mockMvc.perform(post("/api/auth/login").header("Origin", "http://localhost:5173").header("X-SolveGO-CSRF", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("존재하지 않는 username으로 로그인하면 401 Unauthorized를 반환한다")
    void login_fails_when_user_not_found() throws Exception {
        // given
        LoginRequest request = new LoginRequest("unknown-user", "1234");

        // when & then
        mockMvc.perform(post("/api/auth/login").header("Origin", "http://localhost:5173").header("X-SolveGO-CSRF", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    private void signup(String username, String password) throws Exception {
        SignupRequest request = new SignupRequest(username, password);

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        assertThat(userRepository.existsByUsername(username)).isTrue();
    }
}