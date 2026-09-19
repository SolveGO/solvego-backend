package com.kdh.solvego.domain.subscription.billing;

import com.kdh.solvego.domain.payment.exception.PaymentGatewayException;
import com.kdh.solvego.domain.payment.gateway.PaymentGateway;
import com.kdh.solvego.domain.payment.gateway.dto.*;
import com.kdh.solvego.domain.payment.repository.PaymentRepository;
import com.kdh.solvego.domain.payment.type.PaymentStatus;
import com.kdh.solvego.domain.subscription.dto.*;
import com.kdh.solvego.domain.subscription.repository.SubscriptionRepository;
import com.kdh.solvego.domain.subscription.entity.Subscription;
import com.kdh.solvego.domain.subscription.service.SubscriptionEntitlementService;
import com.kdh.solvego.domain.subscription.type.*;
import com.kdh.solvego.domain.user.entity.User;
import com.kdh.solvego.domain.user.repository.UserRepository;
import com.kdh.solvego.domain.ai.service.ExplanationUsageLimitPolicy;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static com.kdh.solvego.domain.payment.exception.PaymentGatewayException.Reason.*;

@SpringBootTest(properties = {"payment.toss.secret-key=test_sk_fixture_only", "payment.pro-monthly-amount=5000"})
@ActiveProfiles("test")
class InitialSubscriptionIntegrationTest {
    @DynamicPropertySource static void keys(DynamicPropertyRegistry registry) {
        String key = BillingKeyCipherTest.randomKey();
        registry.add("payment.billing-encryption-key", () -> key);
    }
    @MockitoBean PaymentGateway gateway;
    @Autowired InitialSubscriptionService service;
    @Autowired UserRepository users;
    @Autowired SubscriptionRepository subscriptions;
    @Autowired PaymentRepository payments;
    @Autowired BillingKeyCipher cipher;
    @Autowired SubscriptionEntitlementService entitlement;
    @Autowired ExplanationUsageLimitPolicy quota;
    Long userId;
    CheckoutResponse checkout;

    @BeforeEach void setup() {
        User user = users.save(new User("billing-" + UUID.randomUUID(), "encoded"));
        userId = user.getId();
        subscriptions.save(Subscription.free(user));
        checkout = service.prepare(userId);
        when(gateway.issueBillingKey(anyString(), anyString())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return new BillingKeyResult("billing-sensitive", invocation.getArgument(1));
        });
        when(gateway.charge(anyString(), anyString(), anyString(), anyString(), anyLong())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return new PaymentApprovalResult("pay-" + UUID.randomUUID(), invocation.getArgument(2),
                    invocation.getArgument(4), Instant.now());
        });
    }

    @AfterEach void cleanup() {
        subscriptions.findByUserId(userId)
                .ifPresent(subscription -> payments.deleteAll(
                        payments.findAllBySubscriptionId(subscription.getId())
                ));
        users.deleteById(userId);
    }

    CompleteCheckoutRequest request() { return new CompleteCheckoutRequest("auth-sensitive", checkout.customerKey()); }

    @Test void initialSuccessPersistsCipherAndActivatesQuotaAndPeriod() {
        assertThat(checkout.status()).isEqualTo(PaymentStatus.READY);
        assertThat(quota.dailyLimitFor(userId)).isEqualTo(5);
        CheckoutResponse result = service.complete(userId, checkout.orderId(), request());
        assertThat(result.status()).isEqualTo(PaymentStatus.SUCCEEDED);
        var payment = payments.findByOrderId(checkout.orderId()).orElseThrow();
        var subscription = subscriptions.findByUserId(userId).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(payment.getAmount()).isEqualTo(5000);
        assertThat(payment.getPaymentKey()).startsWith("pay-");
        assertThat(subscription.getPlan()).isEqualTo(SubscriptionPlan.PRO);
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(subscription.getCurrentPeriodStartAt()).isEqualTo(payment.getApprovedAt());
        assertThat(subscription.getCurrentPeriodEndAt()).isEqualTo(payment.getApprovedAt()
                .atZone(java.time.ZoneId.of("Asia/Seoul")).plusMonths(1).toInstant());
        assertThat(subscription.getNextBillingAt()).isEqualTo(subscription.getCurrentPeriodEndAt());
        assertThat(subscription.isAutoRenew()).isTrue();
        assertThat(cipher.decrypt(subscription.getBillingKeyCiphertext(), checkout.customerKey())).isEqualTo("billing-sensitive");
        assertThat(entitlement.currentPlan(userId)).isEqualTo(SubscriptionPlan.PRO);
        assertThat(quota.dailyLimitFor(userId)).isEqualTo(30);
    }

    @Test void prepareAndCompleteReplaysDoNotChargeAgain() {
        assertThat(service.prepare(userId).orderId()).isEqualTo(checkout.orderId());
        service.complete(userId, checkout.orderId(), request());
        assertThat(service.complete(userId, checkout.orderId(), request()).status()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(service.prepare(userId).orderId()).isEqualTo(checkout.orderId());
        verify(gateway, times(1)).charge(anyString(), anyString(), anyString(), anyString(), eq(5000L));
        verify(gateway, times(1)).issueBillingKey(anyString(), anyString());
    }

    @Test void definitiveDeclineIsFailedWithoutPro() {
        when(gateway.charge(anyString(), anyString(), anyString(), anyString(), anyLong()))
                .thenThrow(new PaymentGatewayException(HTTP_ERROR, 403, "REJECT_CARD_COMPANY"));
        assertThat(service.complete(userId, checkout.orderId(), request()).status()).isEqualTo(PaymentStatus.FAILED);
        var payment = payments.findByOrderId(checkout.orderId()).orElseThrow();
        assertThat(payment.getFailureCode()).isEqualTo("REJECT_CARD_COMPANY");
        assertThat(payment.getFailedAt()).isNotNull();
        assertThat(quota.dailyLimitFor(userId)).isEqualTo(5);
        service.complete(userId, checkout.orderId(), request());
        verify(gateway, times(1)).charge(anyString(), anyString(), anyString(), anyString(), anyLong());
    }

    @Test void timeoutLeavesUnknownAndNeverRetries() {
        when(gateway.charge(anyString(), anyString(), anyString(), anyString(), anyLong()))
                .thenThrow(new PaymentGatewayException(COMMUNICATION_ERROR, null, null));
        assertThat(service.complete(userId, checkout.orderId(), request()).status()).isEqualTo(PaymentStatus.UNKNOWN);
        assertThat(payments.findByOrderId(checkout.orderId()).orElseThrow().getFailedAt()).isNull();
        assertThat(service.prepare(userId).status()).isEqualTo(PaymentStatus.UNKNOWN);
        service.complete(userId, checkout.orderId(), request());
        verify(gateway, times(1)).charge(anyString(), anyString(), anyString(), anyString(), anyLong());
        assertThat(quota.dailyLimitFor(userId)).isEqualTo(5);
    }

    @Test void issuanceFailureDoesNotCharge() {
        when(gateway.issueBillingKey(anyString(), anyString()))
                .thenThrow(new PaymentGatewayException(HTTP_ERROR, 400, "INVALID_AUTH_KEY"));
        assertThat(service.complete(userId, checkout.orderId(), request()).status()).isEqualTo(PaymentStatus.FAILED);
        verify(gateway, never()).charge(anyString(), anyString(), anyString(), anyString(), anyLong());
    }

    @Test void customerMismatchIsRejectedBeforeClaim() {
        assertThatThrownBy(() -> service.complete(userId, checkout.orderId(),
                new CompleteCheckoutRequest("auth-sensitive", "wrong-customer"))).isInstanceOf(RuntimeException.class);
        assertThat(service.status(userId, checkout.orderId()).status()).isEqualTo(PaymentStatus.READY);
        verifyNoInteractions(gateway);
    }

    @Test void otherUserCannotReadOrCompleteOrder() {
        User other = users.save(new User("other-" + UUID.randomUUID(), "encoded"));
        subscriptions.save(Subscription.free(other));
        try {
            assertThatThrownBy(() -> service.status(other.getId(), checkout.orderId())).isInstanceOf(RuntimeException.class);
            assertThatThrownBy(() -> service.complete(other.getId(), checkout.orderId(), request())).isInstanceOf(RuntimeException.class);
            verifyNoInteractions(gateway);
        } finally { users.deleteById(other.getId()); }
    }

    @Test void concurrentPreparationCreatesOneInitialOrder() throws Exception {
        payments.delete(payments.findByOrderId(checkout.orderId()).orElseThrow());
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<CheckoutResponse> prepare = () -> { start.await(); return service.prepare(userId); };
            Future<CheckoutResponse> first = executor.submit(prepare);
            Future<CheckoutResponse> second = executor.submit(prepare);
            start.countDown();
            checkout = first.get(10, TimeUnit.SECONDS);
            assertThat(second.get(10, TimeUnit.SECONDS).orderId()).isEqualTo(checkout.orderId());
            verifyNoInteractions(gateway);
        } finally { executor.shutdownNow(); }
    }

    @Test void definitiveFailureCanPrepareANewOrder() {
        when(gateway.charge(anyString(), anyString(), anyString(), anyString(), anyLong()))
                .thenThrow(new PaymentGatewayException(HTTP_ERROR, 403, "REJECT_CARD_COMPANY"));
        assertThat(service.complete(userId, checkout.orderId(), request()).status())
                .isEqualTo(PaymentStatus.FAILED);

        CheckoutResponse retry = service.prepare(userId);

        assertThat(retry.status()).isEqualTo(PaymentStatus.READY);
        assertThat(retry.orderId()).isNotEqualTo(checkout.orderId());
        assertThat(payments.findByOrderId(checkout.orderId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.FAILED);
    }

    @Test void januaryMonthEndBecomesFebruaryMonthEnd() {
        when(gateway.charge(anyString(), anyString(), anyString(), anyString(), anyLong()))
                .thenReturn(new PaymentApprovalResult("pay-" + UUID.randomUUID(), checkout.orderId(), 5000,
                        Instant.parse("2026-01-31T03:00:00Z")));
        CheckoutResponse result = service.complete(userId, checkout.orderId(), request());
        assertThat(result.currentPeriodEndAt()).isEqualTo(Instant.parse("2026-02-28T03:00:00Z"));
    }

    @Test void concurrentCompletionOnlyOneRequestCallsToss() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(gateway.issueBillingKey(anyString(), anyString())).thenAnswer(invocation -> {
            entered.countDown();
            assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
            return new BillingKeyResult("billing-sensitive", checkout.customerKey());
        });
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<CheckoutResponse> first = executor.submit(() -> service.complete(userId, checkout.orderId(), request()));
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(service.complete(userId, checkout.orderId(), request()).status()).isEqualTo(PaymentStatus.PROCESSING);
            release.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS).status()).isEqualTo(PaymentStatus.SUCCEEDED);
            verify(gateway, times(1)).charge(anyString(), anyString(), anyString(), anyString(), anyLong());
        } finally { release.countDown(); executor.shutdownNow(); }
    }
}
