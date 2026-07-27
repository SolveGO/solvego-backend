package com.kdh.solvego.domain.user.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserControllerConcurrencyTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final String RACE_USERNAME = "concurrent-user";

    @AfterEach
    void tearDown() {
        jdbcTemplate.update(
                "delete from users where username = ?",
                RACE_USERNAME
        );
    }

    @Test
    @DisplayName("동시에 같은 username으로 회원가입해도 하나의 사용자만 생성된다")
    void concurrentSignupWithSameUsernameCreatesOnlyOneUser() throws Exception {
        // given
        int threadCount = 10;
        String password = "1234";

        ExecutorService executorService =
                Executors.newFixedThreadPool(threadCount);

        CountDownLatch readyLatch =
                new CountDownLatch(threadCount);

        CountDownLatch startLatch =
                new CountDownLatch(1);

        List<Future<Integer>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < threadCount; i++) {
                futures.add(executorService.submit(() -> {
                    readyLatch.countDown();
                    startLatch.await();

                    MvcResult result = mockMvc.perform(
                                    post("/api/users")
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(signupRequestJson(
                                                    RACE_USERNAME,
                                                    password
                                            ))
                            )
                            .andReturn();

                    return result.getResponse().getStatus();
                }));
            }

            readyLatch.await();
            startLatch.countDown();

            List<Integer> statuses = new ArrayList<>();

            for (Future<Integer> future : futures) {
                statuses.add(future.get());
            }

            // then
            long successCount = statuses.stream()
                    .filter(status -> status == 201)
                    .count();

            assertThat(successCount).isEqualTo(1);

            Integer savedUserCount = jdbcTemplate.queryForObject(
                    "select count(*) from users where username = ?",
                    Integer.class,
                    RACE_USERNAME
            );

            assertThat(savedUserCount).isEqualTo(1);
        } finally {
            executorService.shutdown();
        }
    }

    private String signupRequestJson(
            String username,
            String password
    ) {
        return """
                {
                  "username": "%s",
                  "password": "%s"
                }
                """.formatted(username, password);
    }
}