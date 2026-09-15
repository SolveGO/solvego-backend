package com.kdh.solvego.domain.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdh.solvego.domain.auth.dto.LoginRequest;
import com.kdh.solvego.domain.auth.dto.AuthTokens;
import com.kdh.solvego.domain.auth.exception.InvalidLoginException;
import com.kdh.solvego.domain.auth.service.AuthService;
import com.kdh.solvego.global.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@org.springframework.context.annotation.Import(RefreshCookie.class)
@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    @DisplayName("로그인에 성공하면 200 OK와 accessToken을 반환한다")
    void login_success() throws Exception {
        // given
        LoginRequest request = new LoginRequest("username", "1234");

        when(authService.login(any(LoginRequest.class)))
                .thenReturn(new AuthTokens("access-token", "a".repeat(43)));

        // when & then
        mockMvc.perform(post("/api/auth/login").header("Origin", "http://localhost:5173").header("X-SolveGO-CSRF", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.accessToken").value("access-token"));

        verify(authService).login(request);
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

        verify(authService, never()).login(any(LoginRequest.class));
    }

    @Test
    @DisplayName("아이디 또는 비밀번호가 틀리면 401 Unauthorized를 반환한다")
    void login_fails_when_username_or_password_is_invalid() throws Exception {
        // given
        LoginRequest request = new LoginRequest("username", "wrong-password");

        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new InvalidLoginException());

        // when & then
        mockMvc.perform(post("/api/auth/login").header("Origin", "http://localhost:5173").header("X-SolveGO-CSRF", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());

        verify(authService).login(request);
    }
}