package com.kdh.solvego.domain.payment.repository;

import com.kdh.solvego.domain.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.time.Instant;
import com.kdh.solvego.domain.payment.type.PaymentType;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    default Optional<Payment> findLatestInitial(Long subscriptionId) {
        return findFirstBySubscriptionIdAndTypeOrderByRequestedAtDesc(subscriptionId, PaymentType.INITIAL);
    }

    default Optional<Payment> findRenewal(Long subscriptionId, Instant renewalCycleAt) {
        return findBySubscriptionIdAndTypeAndRenewalCycleAt(subscriptionId, PaymentType.RENEWAL, renewalCycleAt);
    }

    Optional<Payment> findFirstBySubscriptionIdAndTypeOrderByRequestedAtDesc(
            Long subscriptionId,
            PaymentType type
    );
    Optional<Payment> findBySubscriptionIdAndTypeAndRenewalCycleAt(
            Long subscriptionId,
            PaymentType type,
            Instant renewalCycleAt
    );
    Optional<Payment> findByOrderId(String orderId);
    List<Payment> findAllBySubscriptionIdOrderByRequestedAtDesc(Long subscriptionId);
}
