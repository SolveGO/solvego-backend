package com.kdh.solvego.domain.payment.repository;

import com.kdh.solvego.domain.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.time.Instant;
import com.kdh.solvego.domain.payment.type.PaymentType;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findFirstBySubscriptionIdAndTypeOrderByRequestedAtDescIdDesc(
            Long subscriptionId,
            PaymentType type
    );
    Optional<Payment> findBySubscriptionIdAndTypeAndBillingCycleAt(
            Long subscriptionId,
            PaymentType type,
            Instant billingCycleAt
    );
    Optional<Payment> findByOrderId(String orderId);
    List<Payment> findAllBySubscriptionId(Long subscriptionId);
}
