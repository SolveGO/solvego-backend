package com.kdh.solvego.domain.subscription.controller;

import com.kdh.solvego.domain.subscription.billing.InitialSubscriptionService;
import com.kdh.solvego.domain.subscription.dto.*;
import com.kdh.solvego.domain.subscription.exception.SubscriptionCheckoutException;
import com.kdh.solvego.domain.payment.type.PaymentStatus;
import com.kdh.solvego.global.security.SecurityConfig;
import com.kdh.solvego.global.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SubscriptionCheckoutController.class)
@Import(SecurityConfig.class)
class SubscriptionCheckoutControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean InitialSubscriptionService service;
    @MockitoBean JwtTokenProvider jwt;
    String base = "/api/subscriptions/pro/checkouts";

    CheckoutResponse response(PaymentStatus status) {
        return new CheckoutResponse("order-123", "customer", "PRO", 5000, status, null);
    }

    @Test void allEndpointsRequireBearerAuthentication() throws Exception {
        mvc.perform(post(base)).andExpect(status().isUnauthorized());
        mvc.perform(get(base + "/order-123")).andExpect(status().isUnauthorized());
        mvc.perform(post(base + "/order-123/complete").contentType(MediaType.APPLICATION_JSON)
                .content("{\"authKey\":\"auth\",\"customerKey\":\"customer\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test void prepareUsesJwtPrincipalAndReturnsNoStore() throws Exception {
        when(jwt.getUserId("fixture-token")).thenReturn(42L);
        when(service.prepare(42L)).thenReturn(response(PaymentStatus.READY));
        mvc.perform(post(base).header("Authorization", "Bearer fixture-token"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.amount").value(5000)).andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.billingKey").doesNotExist()).andExpect(jsonPath("$.secretKey").doesNotExist());
    }

    @Test void completeAndStatusUseAuthenticatedOwner() throws Exception {
        when(jwt.getUserId("fixture-token")).thenReturn(42L);
        when(service.complete(eq(42L), eq("order-123"), any())).thenReturn(response(PaymentStatus.SUCCEEDED));
        when(service.status(42L, "order-123")).thenReturn(response(PaymentStatus.UNKNOWN));
        mvc.perform(post(base + "/order-123/complete").header("Authorization", "Bearer fixture-token")
                .contentType(MediaType.APPLICATION_JSON).content("{\"authKey\":\"auth\",\"customerKey\":\"customer\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUCCEEDED"));
        verify(service).complete(42L, "order-123", new CompleteCheckoutRequest("auth", "customer"));
        mvc.perform(get(base + "/order-123").header("Authorization", "Bearer fixture-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UNKNOWN"));
    }

    @Test void invalidBodyIsRejectedWithoutCallingService() throws Exception {
        when(jwt.getUserId("fixture-token")).thenReturn(42L);
        mvc.perform(post(base + "/order-123/complete").header("Authorization", "Bearer fixture-token")
                .contentType(MediaType.APPLICATION_JSON).content("{\"authKey\":\"\",\"customerKey\":\"customer\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test void conflictUsesExistingErrorStyle() throws Exception {
        when(jwt.getUserId("fixture-token")).thenReturn(42L);
        when(service.prepare(42L)).thenThrow(new SubscriptionCheckoutException("이미 PRO 구독 중입니다."));
        mvc.perform(post(base).header("Authorization", "Bearer fixture-token"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("이미 PRO 구독 중입니다."));
    }
}
