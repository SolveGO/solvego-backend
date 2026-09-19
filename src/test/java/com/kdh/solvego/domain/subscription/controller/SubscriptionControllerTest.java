package com.kdh.solvego.domain.subscription.controller;

import com.kdh.solvego.domain.subscription.dto.SubscriptionResponse;
import com.kdh.solvego.domain.subscription.service.SubscriptionManagementService;
import com.kdh.solvego.domain.subscription.type.SubscriptionPlan;
import com.kdh.solvego.domain.subscription.type.SubscriptionStatus;
import com.kdh.solvego.global.security.SecurityConfig;
import com.kdh.solvego.global.security.jwt.JwtTokenProvider;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SubscriptionController.class)
@Import(SecurityConfig.class)
class SubscriptionControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean SubscriptionManagementService service;
    @MockitoBean JwtTokenProvider jwt;

    SubscriptionResponse response(boolean autoRenew, boolean cancelAtPeriodEnd) {
        return new SubscriptionResponse(
                SubscriptionPlan.PRO,
                SubscriptionStatus.ACTIVE,
                Instant.parse("2026-09-19T00:00:00Z"),
                Instant.parse("2026-10-19T00:00:00Z"),
                Instant.parse("2026-10-19T00:00:00Z"),
                autoRenew,
                cancelAtPeriodEnd
        );
    }

    @Test
    void endpointsRequireAuthentication() throws Exception {
        mvc.perform(get("/api/subscriptions/me")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/subscriptions/me/cancel-at-period-end")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/subscriptions/me/reactivate")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void currentCancelAndReactivateUseJwtOwner() throws Exception {
        when(jwt.getUserId("fixture-token")).thenReturn(42L);
        when(service.current(42L)).thenReturn(response(true, false));
        when(service.cancelAtPeriodEnd(42L)).thenReturn(response(false, true));
        when(service.reactivate(42L)).thenReturn(response(true, false));

        mvc.perform(get("/api/subscriptions/me").header("Authorization", "Bearer fixture-token"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.plan").value("PRO"))
                .andExpect(jsonPath("$.autoRenew").value(true));
        mvc.perform(post("/api/subscriptions/me/cancel-at-period-end")
                        .header("Authorization", "Bearer fixture-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cancelAtPeriodEnd").value(true));
        mvc.perform(post("/api/subscriptions/me/reactivate")
                        .header("Authorization", "Bearer fixture-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.autoRenew").value(true));
    }
}
