package com.kdh.solvego.domain.payment.repository;

import com.kdh.solvego.domain.payment.entity.Payment;
import com.kdh.solvego.domain.payment.type.PaymentStatus;
import com.kdh.solvego.domain.payment.type.PaymentType;
import com.kdh.solvego.domain.subscription.entity.Subscription;
import com.kdh.solvego.domain.subscription.repository.SubscriptionRepository;
import com.kdh.solvego.domain.user.entity.User;
import com.kdh.solvego.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PaymentRepositoryTest {
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("최초 결제 이력과 성공 상태를 저장한다")
    void payment_history_is_persisted() {
        User user = userRepository.save(new User("payer", "encoded"));
        Subscription subscription = subscriptionRepository.save(
                Subscription.free(user)
        );
        Payment payment = new Payment(
                subscription,
                "order-pro-000001",
                PaymentType.INITIAL,
                9900
        );
        payment.markSucceeded(
                "payment-key-000001",
                Instant.parse("2026-09-16T00:00:00Z")
        );
        paymentRepository.saveAndFlush(payment);
        entityManager.clear();

        Payment found = paymentRepository.findByOrderId("order-pro-000001")
                .orElseThrow();

        assertThat(found.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(found.getType()).isEqualTo(PaymentType.INITIAL);
        assertThat(found.getAmount()).isEqualTo(9900);
    }

    @Test
    @DisplayName("같은 구독과 청구 주기의 갱신 결제는 하나만 저장한다")
    void renewal_cycle_is_unique_per_subscription() {
        User user = userRepository.save(new User("renewal-payer", "encoded"));
        Subscription subscription = subscriptionRepository.save(Subscription.free(user));
        Instant billingCycleAt = Instant.parse("2026-10-01T00:00:00Z");

        paymentRepository.saveAndFlush(Payment.renewal(
                subscription,
                "renewal-order-1",
                9900,
                billingCycleAt
        ));

        assertThatThrownBy(() -> paymentRepository.saveAndFlush(Payment.renewal(
                subscription,
                "renewal-order-2",
                9900,
                billingCycleAt
        ))).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
