package com.kdh.solvego.domain.subscription.billing;

import com.kdh.solvego.domain.payment.exception.PaymentGatewayException;
import com.kdh.solvego.domain.payment.gateway.PaymentGateway;
import com.kdh.solvego.domain.payment.gateway.dto.PaymentApprovalResult;
import com.kdh.solvego.domain.payment.repository.PaymentRepository;
import com.kdh.solvego.domain.payment.type.PaymentStatus;
import com.kdh.solvego.domain.payment.type.PaymentType;
import com.kdh.solvego.domain.subscription.entity.Subscription;
import com.kdh.solvego.domain.subscription.repository.SubscriptionRepository;
import com.kdh.solvego.domain.user.entity.User;
import com.kdh.solvego.domain.user.repository.UserRepository;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static com.kdh.solvego.domain.payment.exception.PaymentGatewayException.Reason.COMMUNICATION_ERROR;
import static com.kdh.solvego.domain.payment.exception.PaymentGatewayException.Reason.HTTP_ERROR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "payment.toss.secret-key=test_sk_fixture_only",
        "payment.pro-monthly-amount=5000",
        "payment.renewal.scheduler-enabled=false"
})
@ActiveProfiles("test")
class RenewalSubscriptionIntegrationTest {
    private static final String ENCRYPTION_KEY = BillingKeyCipherTest.randomKey();
    private static final Instant PERIOD_START = Instant.parse("2026-07-31T03:00:00Z");
    private static final Instant BILLING_CYCLE = Instant.parse("2026-08-31T03:00:00Z");

    @DynamicPropertySource
    static void encryptionKey(DynamicPropertyRegistry registry) {
        registry.add("payment.billing-encryption-key", () -> ENCRYPTION_KEY);
    }

    @MockitoBean PaymentGateway gateway;
    @Autowired RenewalSubscriptionService service;
    @Autowired BillingKeyCipher cipher;
    @Autowired UserRepository users;
    @Autowired SubscriptionRepository subscriptions;
    @Autowired PaymentRepository payments;

    private Long userId;
    private Long subscriptionId;

    @BeforeEach
    void setup() {
        User user = users.save(new User("renewal-" + UUID.randomUUID(), "encoded"));
        userId = user.getId();
        Subscription subscription = Subscription.free(user);
        subscription.initializeCustomerKey("customer-" + UUID.randomUUID());
        subscription.storeBillingKey(cipher.encrypt("billing-sensitive", subscription.getCustomerKey()));
        subscription.activatePaidPro(PERIOD_START, BILLING_CYCLE);
        subscriptionId = subscriptions.saveAndFlush(subscription).getId();

        when(gateway.charge(anyString(), anyString(), anyString(), anyString(), anyLong()))
                .thenAnswer(invocation -> {
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                    return new PaymentApprovalResult(
                            "pay-" + UUID.randomUUID(),
                            invocation.getArgument(2),
                            invocation.getArgument(4),
                            Instant.now()
                    );
                });
    }

    @AfterEach
    void cleanup() {
        payments.deleteAll(payments.findAll().stream()
                .filter(payment -> payment.getSubscription() != null
                        && payment.getSubscription().getId().equals(subscriptionId))
                .toList());
        users.deleteById(userId);
    }

    @Test
    void dueSubscriptionCreatesRenewalPaymentAndAdvancesOnePeriod() {
        assertThat(service.renewDueSubscriptions()).isEqualTo(1);

        var payment = payments.findBySubscriptionIdAndTypeAndBillingCycleAt(
                subscriptionId,
                PaymentType.RENEWAL,
                BILLING_CYCLE
        ).orElseThrow();
        var subscription = subscriptions.findById(subscriptionId).orElseThrow();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(payment.getAmount()).isEqualTo(5000);
        assertThat(subscription.getCurrentPeriodStartAt()).isEqualTo(BILLING_CYCLE);
        assertThat(subscription.getCurrentPeriodEndAt())
                .isEqualTo(Instant.parse("2026-09-30T03:00:00Z"));
        assertThat(subscription.getNextBillingAt()).isEqualTo(subscription.getCurrentPeriodEndAt());
        verify(gateway, times(1)).charge(
                eq("billing-sensitive"),
                eq(subscription.getCustomerKey()),
                eq(payment.getOrderId()),
                eq("SolveGO PRO 1개월"),
                eq(5000L)
        );
    }

    @Test
    void unknownResultIsNotAutomaticallyChargedAgain() {
        when(gateway.charge(anyString(), anyString(), anyString(), anyString(), anyLong()))
                .thenThrow(new PaymentGatewayException(COMMUNICATION_ERROR, null, null));

        assertThat(service.renewDueSubscriptions()).isEqualTo(1);
        assertThat(service.renewDueSubscriptions()).isZero();

        var payment = payments.findBySubscriptionIdAndTypeAndBillingCycleAt(
                subscriptionId,
                PaymentType.RENEWAL,
                BILLING_CYCLE
        ).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.UNKNOWN);
        assertThat(payment.getFailedAt()).isNull();
        verify(gateway, times(1)).charge(anyString(), anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    void explicitDeclineIsFailedWithoutAdvancingSubscription() {
        when(gateway.charge(anyString(), anyString(), anyString(), anyString(), anyLong()))
                .thenThrow(new PaymentGatewayException(HTTP_ERROR, 403, "REJECT_CARD_COMPANY"));

        assertThat(service.renewDueSubscriptions()).isEqualTo(1);

        var payment = payments.findBySubscriptionIdAndTypeAndBillingCycleAt(
                subscriptionId,
                PaymentType.RENEWAL,
                BILLING_CYCLE
        ).orElseThrow();
        var subscription = subscriptions.findById(subscriptionId).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailureCode()).isEqualTo("REJECT_CARD_COMPANY");
        assertThat(subscription.getNextBillingAt()).isEqualTo(BILLING_CYCLE);
    }

    @Test
    void concurrentSchedulersClaimOnePaymentAndCallTossOnce() throws Exception {
        CountDownLatch enteredGateway = new CountDownLatch(1);
        CountDownLatch releaseGateway = new CountDownLatch(1);
        when(gateway.charge(anyString(), anyString(), anyString(), anyString(), anyLong()))
                .thenAnswer(invocation -> {
                    enteredGateway.countDown();
                    assertThat(releaseGateway.await(10, TimeUnit.SECONDS)).isTrue();
                    return new PaymentApprovalResult(
                            "pay-concurrent",
                            invocation.getArgument(2),
                            invocation.getArgument(4),
                            Instant.now()
                    );
                });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = executor.submit(service::renewDueSubscriptions);
            assertThat(enteredGateway.await(10, TimeUnit.SECONDS)).isTrue();
            Future<Integer> second = executor.submit(service::renewDueSubscriptions);

            assertThat(second.get(10, TimeUnit.SECONDS)).isZero();
            releaseGateway.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(1);
            verify(gateway, times(1)).charge(anyString(), anyString(), anyString(), anyString(), anyLong());
            assertThat(payments.findBySubscriptionIdAndTypeAndBillingCycleAt(
                    subscriptionId,
                    PaymentType.RENEWAL,
                    BILLING_CYCLE
            )).get().extracting(payment -> payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        } finally {
            releaseGateway.countDown();
            executor.shutdownNow();
        }
    }
}
