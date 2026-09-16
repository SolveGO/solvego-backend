package com.kdh.solvego.domain.subscription.billing;

import com.kdh.solvego.domain.payment.exception.PaymentGatewayException;
import com.kdh.solvego.domain.payment.gateway.PaymentGateway;
import com.kdh.solvego.domain.payment.gateway.dto.*;
import com.kdh.solvego.domain.payment.type.PaymentStatus;
import com.kdh.solvego.domain.subscription.dto.*;
import java.time.Instant;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class InitialSubscriptionServiceTest {
    CheckoutTransactions tx = mock(CheckoutTransactions.class);
    PaymentGateway gateway = mock(PaymentGateway.class);
    InitialSubscriptionService service = new InitialSubscriptionService(tx, gateway,
            new BillingKeyCipher(BillingKeyCipherTest.randomKey()), 5000, "test_sk_fixture_only");
    CheckoutResponse checkout = new CheckoutResponse("order-123", "customer", "PRO", 5000,
            PaymentStatus.PROCESSING, null);
    CompleteCheckoutRequest request = new CompleteCheckoutRequest("auth", "customer");

    @BeforeEach void setup() {
        when(tx.claim(1L, "order-123", "customer")).thenReturn(new CheckoutTransactions.Claim(true, checkout));
        when(gateway.issueBillingKey("auth", "customer")).thenReturn(new BillingKeyResult("billing", "customer"));
        when(gateway.charge("billing", "customer", "order-123", "PRO", 5000))
                .thenReturn(new PaymentApprovalResult("pay", "order-123", 5000, Instant.now()));
    }

    @Test void approvalCommitFailureRemainsUncertainWithoutRetry() {
        when(tx.succeed(eq(1L), eq("order-123"), any())).thenThrow(new IllegalStateException("DB offline"));
        service.complete(1L, "order-123", request);
        verify(tx).fail(1L, "order-123", null, true);
        verify(gateway, times(1)).charge(anyString(), anyString(), anyString(), anyString(), anyLong());
    }

    @Test void billingKeyPersistenceFailureNeverCharges() {
        doThrow(new IllegalStateException()).when(tx).storeBillingKey(eq(1L), eq("order-123"), anyString());
        service.complete(1L, "order-123", request);
        verify(gateway, never()).charge(anyString(), anyString(), anyString(), anyString(), anyLong());
        verify(tx).fail(1L, "order-123", "CHECKOUT_SETUP_FAILED", false);
    }

    @Test void totalDbOutageDoesNotExposeCauseOrRetry() {
        when(tx.succeed(eq(1L), eq("order-123"), any())).thenThrow(new IllegalStateException("secret"));
        when(tx.fail(1L, "order-123", null, true)).thenThrow(new IllegalStateException("secret"));
        assertThatThrownBy(() -> service.complete(1L, "order-123", request)).hasNoCause()
                .hasMessageNotContaining("secret");
        verify(gateway, times(1)).charge(anyString(), anyString(), anyString(), anyString(), anyLong());
    }

    @ParameterizedTest @ValueSource(ints = {400, 408, 409, 429, 500, 504})
    void ambiguousHttpErrorsNeedReconciliation(int status) {
        when(gateway.charge(anyString(), anyString(), anyString(), anyString(), anyLong()))
                .thenThrow(new PaymentGatewayException(PaymentGatewayException.Reason.HTTP_ERROR, status, "ALREADY_PROCESSED_PAYMENT"));
        service.complete(1L, "order-123", request);
        verify(tx).fail(1L, "order-123", null, true);
    }

    @Test void rejectsLiveKeyBeforeCreatingOrder() {
        var live = new InitialSubscriptionService(tx, gateway, new BillingKeyCipher(BillingKeyCipherTest.randomKey()),
                5000, "live_sk_fixture_only");
        assertThatThrownBy(() -> live.prepare(1L)).isInstanceOf(RuntimeException.class);
        verify(tx, never()).prepare(anyLong(), anyLong());
    }
}
