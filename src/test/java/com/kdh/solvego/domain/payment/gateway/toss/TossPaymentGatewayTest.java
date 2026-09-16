package com.kdh.solvego.domain.payment.gateway.toss;

import com.kdh.solvego.domain.payment.exception.PaymentGatewayException;
import com.kdh.solvego.domain.payment.gateway.dto.BillingKeyResult;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static com.kdh.solvego.domain.payment.exception.PaymentGatewayException.Reason.*;

class TossPaymentGatewayTest {
    private MockRestServiceServer server;
    private RestClient client;
    private TossPaymentGateway gateway;
    private static final String BASE = "https://api.tosspayments.com";
    private static final String SECRET = "test_sk_unit_test_only";
    private static final String APPROVED = """
            {"paymentKey":"payment-123", "orderId":"order-123", "totalAmount":9900,
             "status":"DONE", "approvedAt":"2026-09-16T12:30:00+09:00",
             "card":{"number":"1234****"}, "newField":"ignored"}
            """;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        client = builder.build();
        gateway = new TossPaymentGateway(client, SECRET);
    }

    @AfterEach
    void verify() { server.verify(); }

    @Test
    void issuesBillingKeyWithBasicAuthAndRequiredBody() {
        server.expect(requestTo(BASE + "/v1/billing/authorizations/issue"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Basic " + Base64.getEncoder()
                        .encodeToString((SECRET + ":").getBytes(StandardCharsets.UTF_8))))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {"authKey":"auth-secret", "customerKey":"customer-123"}
                        """, true))
                .andRespond(withSuccess("""
                        {"billingKey":"billing-secret", "customerKey":"customer-123",
                         "authenticatedAt":"2026-09-16T12:00:00+09:00", "card":{}}
                        """, MediaType.APPLICATION_JSON));
        BillingKeyResult result = gateway.issueBillingKey("auth-secret", "customer-123");
        assertThat(result.billingKey()).isEqualTo("billing-secret");
        assertThat(result.customerKey()).isEqualTo("customer-123");
        assertThat(result.toString()).doesNotContain("billing-secret", "customer-123");
    }

    @Test
    void approvesPaymentAndConvertsAmountAndTime() {
        server.expect(requestTo(BASE + "/v1/billing/billing-secret"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Basic " + Base64.getEncoder()
                        .encodeToString((SECRET + ":").getBytes(StandardCharsets.UTF_8))))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {"customerKey":"customer-123", "orderId":"order-123",
                         "orderName":"SolveGO PRO", "amount":9900}
                        """, true))
                .andRespond(withSuccess(APPROVED, MediaType.APPLICATION_JSON));
        var result = charge();
        assertThat(result.paymentKey()).isEqualTo("payment-123");
        assertThat(result.orderId()).isEqualTo("order-123");
        assertThat(result.amount()).isEqualTo(9900);
        assertThat(result.approvedAt()).isEqualTo(Instant.parse("2026-09-16T03:30:00Z"));
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 429, 500, 502, 504, 302})
    void httpErrorsAreSanitizedForBothOperations(int status) {
        for (boolean billing : new boolean[]{true, false}) {
            server.reset();
            server.expect(anything()).andRespond(withStatus(HttpStatusCode.valueOf(status))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""
                            {"code":"REJECT_CARD_COMPANY", "message":"billing-secret auth-secret test_sk_unit_test_only"}
                            """));
            assertThatThrownBy(() -> invoke(billing)).isInstanceOfSatisfying(PaymentGatewayException.class, e -> {
                assertThat(e.getReason()).isEqualTo(HTTP_ERROR);
                assertThat(e.getHttpStatus()).isEqualTo(status);
                assertThat(e.getFailureCode()).isEqualTo("REJECT_CARD_COMPANY");
                assertThat(e).hasNoCause();
                assertThat(e.getMessage()).doesNotContain("billing-secret", "auth-secret", SECRET);
            });
            server.verify();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "<html>error</html>", "{}", "{\"code\":\"billing-secret\"}"})
    void malformedErrorBodyStillProducesHttpError(String body) {
        server.expect(anything()).andRespond(withStatus(HttpStatusCode.valueOf(500))
                .contentType(MediaType.APPLICATION_JSON).body(body));
        assertThatThrownBy(this::charge).isInstanceOfSatisfying(PaymentGatewayException.class, e -> {
            assertThat(e.getReason()).isEqualTo(HTTP_ERROR);
            assertThat(e.getFailureCode()).isNull();
        });
    }

    @Test
    void transportFailureDoesNotRetainSensitiveUrlOrRetry() {
        server.expect(anything()).andRespond(withException(new SocketTimeoutException(
                BASE + "/v1/billing/billing-secret")));
        assertThatThrownBy(this::charge).isInstanceOfSatisfying(PaymentGatewayException.class, e -> {
            assertThat(e.getReason()).isEqualTo(COMMUNICATION_ERROR);
            assertThat(e).hasNoCause();
            assertThat(e.getMessage()).doesNotContain("billing-secret");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "{}", "not-json",
            "{\"billingKey\":\"billing-secret\",\"customerKey\":\"other\"}"})
    void rejectsInvalidBillingResponse(String body) {
        server.expect(anything()).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        assertInvalid(() -> gateway.issueBillingKey("auth-secret", "customer-123"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "{}", "not-json"})
    void rejectsEmptyOrMalformedApproval(String body) {
        server.expect(anything()).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        assertInvalid(this::charge);
    }

    @ParameterizedTest
    @ValueSource(strings = {"payment-123", "order-123", "9900", "DONE", "2026-09-16T12:30:00+09:00"})
    void rejectsMissingOrMismatchedApprovalFields(String value) {
        String body = value.equals("9900") ? APPROVED.replace(value, "9901")
                : APPROVED.replace(value, "");
        server.expect(anything()).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        assertInvalid(this::charge);
    }

    @Test
    void missingSecretFailsBeforeNetworkCall() {
        gateway = new TossPaymentGateway(client, "");
        assertThatThrownBy(this::charge).isInstanceOfSatisfying(PaymentGatewayException.class,
                e -> assertThat(e.getReason()).isEqualTo(NOT_CONFIGURED));
    }

    @Test
    void invalidArgumentsFailBeforeNetworkCall() {
        assertThatThrownBy(() -> gateway.issueBillingKey("", "customer-123"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> gateway.charge("billing-secret", "customer-123", "order-123", "PRO", 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sensitiveInternalDtosHaveRedactedToString() {
        assertThat(new TossDtos.BillingRequest("auth-secret", "customer-123").toString())
                .doesNotContain("auth-secret", "customer-123");
        assertThat(new TossDtos.BillingResponse("billing-secret", "customer-123").toString())
                .doesNotContain("billing-secret", "customer-123");
        assertThat(new TossDtos.ChargeRequest("customer-123", "order-123", "PRO", 9900).toString())
                .doesNotContain("customer-123");
    }

    private void invoke(boolean billing) {
        if (billing) gateway.issueBillingKey("auth-secret", "customer-123");
        else charge();
    }

    private com.kdh.solvego.domain.payment.gateway.dto.PaymentApprovalResult charge() {
        return gateway.charge("billing-secret", "customer-123", "order-123", "SolveGO PRO", 9900);
    }

    private void assertInvalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(PaymentGatewayException.class,
                e -> assertThat(e.getReason()).isEqualTo(INVALID_RESPONSE));
    }
}
