package com.kdh.solvego.domain.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kdh.solvego.domain.auth.repository.RefreshSessionRepository;
import com.kdh.solvego.domain.auth.service.RefreshSessionService;
import com.kdh.solvego.domain.user.dto.SignupRequest;
import com.kdh.solvego.domain.user.service.UserService;
import com.kdh.solvego.global.security.jwt.JwtTokenProvider;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@Transactional
class RefreshAuthenticationIntegrationTest {
    @Container
    static GenericContainer<?> redisContainer = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry properties) {
        properties.add("spring.data.redis.host", redisContainer::getHost);
        properties.add("spring.data.redis.port", () -> redisContainer.getMappedPort(6379));
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserService users;
    @Autowired StringRedisTemplate redis;
    @Autowired RefreshSessionService sessions;
    @Autowired JwtTokenProvider jwt;
    @MockitoSpyBean RefreshSessionRepository repository;

    private String username;
    private long userId;

    @BeforeEach
    void setup() {
        username = "refresh-" + UUID.randomUUID().toString().substring(0, 8);
        userId = users.signup(new SignupRequest(username, "password")).userId();
        clearInvocations(repository);
    }

    private MockHttpServletRequestBuilder authPost(String path) {
        return post("/api/auth/" + path).header("Origin", "http://localhost:5173")
                .header("X-SolveGO-CSRF", "1").contentType("application/json").content("{}");
    }

    private MvcResult login() throws Exception {
        return mvc.perform(authPost("login").content(mapper.writeValueAsString(
                Map.of("username", username, "password", "password"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").doesNotExist()).andReturn();
    }

    private String token(MvcResult result) {
        return result.getResponse().getCookie(RefreshCookie.NAME).getValue();
    }

    private String key(String token) throws Exception {
        return "solvego:auth:refresh:" + HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void loginStoresOnlyHashedKeyWithSevenDayTtlAndSecureCookiePolicy() throws Exception {
        var login = login();
        String token = token(login);
        assertThat(token).matches("[A-Za-z0-9_-]{43}");
        String json = redis.opsForValue().get(key(token));
        assertThat(json).doesNotContain(token);
        var session = mapper.readTree(json);
        assertThat(session.get("userId").asLong()).isEqualTo(userId);
        assertThat(session.get("expiresAt").asLong() - session.get("createdAt").asLong()).isEqualTo(604800);
        assertThat(redis.getExpire(key(token), TimeUnit.SECONDS)).isBetween(604790L, 604800L);
        assertThat(login.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(login.getResponse().getHeader("Set-Cookie"))
                .contains("HttpOnly", "Path=/api/auth", "SameSite=Lax", "Max-Age=604800")
                .doesNotContain("Domain=", "; Secure");
        assertThat(new RefreshCookie(true, "None").issue(token))
                .contains("HttpOnly", "Secure", "SameSite=None").doesNotContain("Domain=");
    }

    @Test
    void refreshIssuesAccessWithoutRotationOrTtlExtension() throws Exception {
        String token = token(login());
        String key = key(token);
        // Shorten the Redis TTL to make an accidental reset to seven days observable.
        redis.expire(key, Duration.ofSeconds(90));
        String original = redis.opsForValue().get(key);
        clearInvocations(repository);
        for (int i = 0; i < 2; i++) {
            var result = mvc.perform(authPost("refresh").cookie(new Cookie(RefreshCookie.NAME, token)))
                    .andExpect(status().isOk()).andExpect(header().doesNotExist("Set-Cookie"))
                    .andExpect(jsonPath("$.refreshToken").doesNotExist()).andReturn();
            String access = mapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
            assertThat(jwt.getUserId(access)).isEqualTo(userId);
        }
        assertThat(redis.opsForValue().get(key)).isEqualTo(original);
        assertThat(redis.getExpire(key, TimeUnit.SECONDS)).isBetween(80L, 90L);
        verify(repository, times(2)).find(key);
        verify(repository, never()).save(anyString(), any(), any());
    }

    @Test
    void refreshRejectsMissingUnknownExpiredAndDeletedTokens() throws Exception {
        mvc.perform(authPost("refresh")).andExpect(status().isUnauthorized())
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")));
        mvc.perform(authPost("refresh").cookie(new Cookie(RefreshCookie.NAME, "x".repeat(43))))
                .andExpect(status().isUnauthorized());
        String token = sessions.create(userId);
        repository.save(key(token), new RefreshSessionRepository.Session(userId,
                Instant.now().minusSeconds(604810).getEpochSecond(), Instant.now().minusSeconds(10).getEpochSecond()), Duration.ofMinutes(1));
        mvc.perform(authPost("refresh").cookie(new Cookie(RefreshCookie.NAME, token)))
                .andExpect(status().isUnauthorized());
        redis.delete(key(token));
        mvc.perform(authPost("refresh").cookie(new Cookie(RefreshCookie.NAME, token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutDeletesOnlyThisDeviceAndDoesNotBlacklistAccess() throws Exception {
        var first = login();
        String tokenA = token(first);
        String tokenB = token(login());
        String access = mapper.readTree(first.getResponse().getContentAsString()).get("accessToken").asText();
        mvc.perform(authPost("logout").cookie(new Cookie(RefreshCookie.NAME, tokenA)))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")));
        assertThat(redis.hasKey(key(tokenA))).isFalse();
        assertThat(redis.hasKey(key(tokenB))).isTrue();
        mvc.perform(authPost("refresh").cookie(new Cookie(RefreshCookie.NAME, tokenA)))
                .andExpect(status().isUnauthorized());
        mvc.perform(authPost("refresh").cookie(new Cookie(RefreshCookie.NAME, tokenB)))
                .andExpect(status().isOk());
        clearInvocations(repository);
        mvc.perform(get("/api/users/me/wrong-problems").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk());
        verifyNoInteractions(repository);
        mvc.perform(authPost("logout").cookie(new Cookie(RefreshCookie.NAME, tokenA)))
                .andExpect(status().isNoContent());
    }

    @Test
    void expiredAccessReturns401WithoutRedisAndCanStillRefresh() throws Exception {
        String token = token(login());
        var expiredJwt = new JwtTokenProvider("test-secret-key-test-secret-key-test-secret-key-123456", -1000);
        String expired = expiredJwt.createToken(userId);
        clearInvocations(repository);
        mvc.perform(get("/api/users/me/wrong-problems").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users/me/wrong-problems").cookie(new Cookie(RefreshCookie.NAME, token)))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(repository);
        mvc.perform(authPost("refresh").header("Authorization", "Bearer " + expired)
                        .cookie(new Cookie(RefreshCookie.NAME, token)))
                .andExpect(status().isOk());
    }

    @Test
    void csrfRejectsMissingOriginHeaderAndForeignOriginBeforeAuthWork() throws Exception {
        for (String path : new String[]{"login", "refresh", "logout"}) {
            mvc.perform(post("/api/auth/" + path).contentType("application/json").content("{}"))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/api/auth/" + path).header("Origin", "http://localhost:5173")
                            .contentType("application/json").content("{}"))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/api/auth/" + path).header("Origin", "https://attacker.example")
                            .header("X-SolveGO-CSRF", "1").contentType("application/json").content("{}"))
                    .andExpect(status().isForbidden());
            mvc.perform(authPost(path).contentType("text/plain"))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(repository);
    }

    @Test
    void csrfAlsoProtectsEncodedAuthPathsAndNullOrigin() throws Exception {
        mvc.perform(post(java.net.URI.create("/%61pi/auth/refresh"))
                        .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/refresh").header("Origin", "null")
                        .header("X-SolveGO-CSRF", "1").contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(repository);
    }

    @Test
    void corsPreflightAllowsOnlyConfiguredOriginAndCredentials() throws Exception {
        mvc.perform(options("/api/auth/refresh").header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type,x-solvego-csrf"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
        mvc.perform(options("/api/auth/refresh").header("Origin", "https://attacker.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(repository);
    }

    @Test
    void redisOutageDoesNotBecomeAuthenticationFailureOrClearCookie() throws Exception {
        String token = token(login());
        doThrow(new org.springframework.data.redis.RedisConnectionFailureException("offline"))
                .when(repository).find(key(token));
        mvc.perform(authPost("refresh").cookie(new Cookie(RefreshCookie.NAME, token)))
                .andExpect(status().isServiceUnavailable()).andExpect(header().doesNotExist("Set-Cookie"));
    }
}
