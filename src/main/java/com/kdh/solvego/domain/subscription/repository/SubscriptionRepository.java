package com.kdh.solvego.domain.subscription.repository;

import com.kdh.solvego.domain.subscription.entity.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.time.Instant;
import java.util.List;
import com.kdh.solvego.domain.payment.type.PaymentType;
import org.springframework.data.domain.Pageable;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select s from Subscription s where s.user.id = :userId")
    Optional<Subscription> findByUserIdForUpdate(@org.springframework.data.repository.query.Param("userId") Long userId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select s from Subscription s where s.id = :id")
    Optional<Subscription> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);

    @org.springframework.data.jpa.repository.Query("""
            select s.id from Subscription s
            where s.plan = com.kdh.solvego.domain.subscription.type.SubscriptionPlan.PRO
              and s.status = com.kdh.solvego.domain.subscription.type.SubscriptionStatus.ACTIVE
              and s.autoRenew = true
              and s.cancelAtPeriodEnd = false
              and s.nextBillingAt is not null
              and s.nextBillingAt <= :now
              and s.customerKey is not null
              and s.billingKeyCiphertext is not null
              and not exists (
                  select p.id from Payment p
                  where p.subscription = s
                    and p.type = :paymentType
                    and p.billingCycleAt = s.nextBillingAt
              )
            order by s.nextBillingAt, s.id
            """)
    List<Long> findDueRenewalCandidateIds(
            @org.springframework.data.repository.query.Param("now") Instant now,
            @org.springframework.data.repository.query.Param("paymentType") PaymentType paymentType,
            Pageable pageable
    );

    Optional<Subscription> findByUserId(Long userId);
}
