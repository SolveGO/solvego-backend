package com.kdh.solvego.domain.subscription.billing;

import com.kdh.solvego.domain.payment.exception.PaymentGatewayException;
import com.kdh.solvego.domain.payment.gateway.PaymentGateway;
import com.kdh.solvego.domain.payment.gateway.dto.PaymentApprovalResult;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.kdh.solvego.domain.payment.exception.PaymentGatewayException.Reason.COMMUNICATION_ERROR;
import static com.kdh.solvego.domain.payment.exception.PaymentGatewayException.Reason.HTTP_ERROR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RenewalSubscriptionServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-01T00:00:00Z");

    RenewalTransactions transactions = mock(RenewalTransactions.class);
    PaymentGateway gateway = mock(PaymentGateway.class);
    BillingKeyCipher cipher = mock(BillingKeyCipher.class);
    RenewalSubscriptionService service;

    @BeforeEach
    void setup() {
        when(cipher.isConfigured()).thenReturn(true);
        service = new RenewalSubscriptionService(
                transactions,
                gateway,
                cipher,
                5000,
                100,
                "test_sk_fixture_only",
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        when(transactions.findDueCandidateIds(NOW, 100)).thenReturn(List.of(10L));
        when(transactions.claim(10L, NOW, 5000)).thenReturn(new RenewalTransactions.Claim(
                true,
                "renewal-order",
                "SolveGO PRO 1개월",
                5000,
                "customer",
                "ciphertext"
        ));
        when(cipher.decrypt("ciphertext", "customer")).thenReturn("billing-key");
    }

    @Test
    void successfulRenewalChargesOnceAndCommitsApproval() {
        PaymentApprovalResult approval = new PaymentApprovalResult(
                "payment-key",
                "renewal-order",
                5000,
                NOW
        );
        when(gateway.charge(anyString(), anyString(), anyString(), anyString(), anyLong()))
                .thenReturn(approval);

        assertThat(service.renewDueSubscriptions()).isEqualTo(1);

        verify(gateway).charge(
                "billing-key",
                "customer",
                "renewal-order",
                "SolveGO PRO 1개월",
                5000
        );
        verify(transactions).succeed(10L, "renewal-order", approval);
        verify(transactions, never()).fail(anyLong(), anyString(), any(), anyBoolean());
    }

    @Test
    void explicitCardDeclineIsFailed() {
        when(gateway.charge(anyString(), anyString(), anyString(), anyString(), anyLong()))
                .thenThrow(new PaymentGatewayException(HTTP_ERROR, 403, "REJECT_CARD_COMPANY"));

        assertThat(service.renewDueSubscriptions()).isEqualTo(1);

        verify(transactions).fail(10L, "renewal-order", "REJECT_CARD_COMPANY", false);
    }

    @Test
    void communicationFailureIsUnknown() {
        when(gateway.charge(anyString(), anyString(), anyString(), anyString(), anyLong()))
                .thenThrow(new PaymentGatewayException(COMMUNICATION_ERROR, null, null));

        assertThat(service.renewDueSubscriptions()).isEqualTo(1);

        verify(transactions).fail(10L, "renewal-order", null, true);
    }

    @Test
    void decryptionFailureDoesNotContactToss() {
        when(cipher.decrypt("ciphertext", "customer")).thenThrow(new IllegalStateException());

        assertThat(service.renewDueSubscriptions()).isEqualTo(1);

        verifyNoInteractions(gateway);
        verify(transactions).fail(10L, "renewal-order", "BILLING_KEY_DECRYPT_FAILED", false);
    }
}
