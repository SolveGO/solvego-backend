package com.kdh.solvego.domain.subscription.billing;

import com.kdh.solvego.domain.payment.entity.Payment;
import com.kdh.solvego.domain.payment.gateway.dto.PaymentApprovalResult;
import com.kdh.solvego.domain.payment.repository.PaymentRepository;
import com.kdh.solvego.domain.payment.type.PaymentStatus;
import com.kdh.solvego.domain.payment.type.PaymentType;
import com.kdh.solvego.domain.subscription.entity.Subscription;
import com.kdh.solvego.domain.subscription.repository.SubscriptionRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Short, independent DB transactions. No method in this class calls Toss. */
@Service
@Transactional(propagation = Propagation.REQUIRES_NEW)
public class RenewalTransactions {
    private final SubscriptionRepository subscriptions;
    private final PaymentRepository payments;

    public RenewalTransactions(SubscriptionRepository subscriptions, PaymentRepository payments) {
        this.subscriptions = subscriptions;
        this.payments = payments;
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public List<Long> findDueCandidateIds(Instant now, int limit) {
        return subscriptions.findDueRenewalCandidateIds(
                now,
                PaymentType.RENEWAL,
                PageRequest.of(0, limit)
        );
    }

    public Claim claim(Long subscriptionId, Instant now, long amount) {
        Subscription subscription = subscriptions.findByIdForUpdate(subscriptionId).orElse(null);
        if (subscription == null || !subscription.isRenewalDue(now)) {
            return Claim.notClaimed();
        }

        Instant billingCycleAt = subscription.getNextBillingAt();
        if (payments.findBySubscriptionIdAndTypeAndBillingCycleAt(
                subscriptionId,
                PaymentType.RENEWAL,
                billingCycleAt
        ).isPresent()) {
            return Claim.notClaimed();
        }

        Payment payment = Payment.renewal(
                subscription,
                "renewal-" + UUID.randomUUID(),
                amount,
                billingCycleAt
        );
        payment.startProcessing();
        payments.save(payment);
        return new Claim(
                true,
                payment.getOrderId(),
                payment.getOrderName(),
                payment.getAmount(),
                subscription.getCustomerKey(),
                subscription.getBillingKeyCiphertext()
        );
    }

    public void succeed(Long subscriptionId, String orderId, PaymentApprovalResult approval) {
        Subscription subscription = lock(subscriptionId);
        Payment payment = owned(subscription, orderId);
        if (payment.getStatus() != PaymentStatus.PROCESSING) {
            throw new IllegalStateException("Invalid renewal state");
        }
        if (!orderId.equals(approval.orderId()) || payment.getAmount() != approval.amount()) {
            throw new IllegalStateException("Approval does not match renewal");
        }

        Instant billingCycleAt = payment.getBillingCycleAt();
        if (billingCycleAt == null
                || !billingCycleAt.equals(subscription.getCurrentPeriodEndAt())
                || !billingCycleAt.equals(subscription.getNextBillingAt())) {
            throw new IllegalStateException("Subscription period does not match renewal");
        }

        payment.markSucceeded(approval.paymentKey(), approval.approvedAt());
        subscription.activatePaidPro(billingCycleAt, BillingPeriods.oneMonthAfter(billingCycleAt));
    }

    public void fail(Long subscriptionId, String orderId, String code, boolean uncertain) {
        Subscription subscription = lock(subscriptionId);
        Payment payment = owned(subscription, orderId);
        if (payment.getStatus() == PaymentStatus.PROCESSING) {
            if (uncertain) payment.markUnknown();
            else payment.markFailed(code, Instant.now());
        }
    }

    private Subscription lock(Long subscriptionId) {
        return subscriptions.findByIdForUpdate(subscriptionId)
                .orElseThrow(() -> new IllegalStateException("Subscription not found"));
    }

    private Payment owned(Subscription subscription, String orderId) {
        Payment payment = payments.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalStateException("Renewal payment not found"));
        if (payment.getSubscription() == null
                || !payment.getSubscription().getId().equals(subscription.getId())
                || payment.getType() != PaymentType.RENEWAL) {
            throw new IllegalStateException("Renewal payment ownership mismatch");
        }
        return payment;
    }

    public record Claim(
            boolean claimed,
            String orderId,
            String orderName,
            long amount,
            String customerKey,
            String billingKeyCiphertext
    ) {
        static Claim notClaimed() {
            return new Claim(false, null, null, 0, null, null);
        }
    }
}
