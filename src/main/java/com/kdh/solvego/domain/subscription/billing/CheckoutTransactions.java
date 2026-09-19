package com.kdh.solvego.domain.subscription.billing;

import com.kdh.solvego.domain.payment.entity.Payment;
import com.kdh.solvego.domain.payment.gateway.dto.PaymentApprovalResult;
import com.kdh.solvego.domain.payment.repository.PaymentRepository;
import com.kdh.solvego.domain.payment.type.PaymentStatus;
import com.kdh.solvego.domain.payment.type.PaymentType;
import com.kdh.solvego.domain.subscription.dto.CheckoutResponse;
import com.kdh.solvego.domain.subscription.entity.Subscription;
import com.kdh.solvego.domain.subscription.exception.SubscriptionCheckoutException;
import com.kdh.solvego.domain.subscription.repository.SubscriptionRepository;
import com.kdh.solvego.domain.subscription.type.SubscriptionPlan;
import com.kdh.solvego.domain.user.exception.UserNotFoundException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Every call commits independently before the orchestrator contacts Toss. Lock order: subscription, payment. */
@Service
@Transactional(propagation = Propagation.REQUIRES_NEW)
public class CheckoutTransactions {
    private final SubscriptionRepository subscriptions;
    private final PaymentRepository payments;

    public CheckoutTransactions(SubscriptionRepository subscriptions, PaymentRepository payments) {
        this.subscriptions = subscriptions;
        this.payments = payments;
    }

    public CheckoutResponse prepare(Long userId, long amount) {
        Subscription subscription = lock(userId);
        Payment existing = payments.findFirstBySubscriptionIdAndTypeOrderByRequestedAtDescIdDesc(
                        subscription.getId(), PaymentType.INITIAL)
                .orElse(null);
        if (existing != null && existing.getStatus() != PaymentStatus.FAILED) {
            return response(existing, subscription);
        }
        if (subscription.effectivePlan(Instant.now()) == SubscriptionPlan.PRO) {
            throw new SubscriptionCheckoutException("이미 PRO 구독 중입니다.");
        }
        subscription.initializeCustomerKey(UUID.randomUUID().toString());
        Payment payment = payments.save(new Payment(subscription, "pro-" + UUID.randomUUID(),
                PaymentType.INITIAL, amount));
        return response(payment, subscription);
    }

    public Claim claim(Long userId, String orderId, String customerKey) {
        Subscription subscription = lock(userId);
        Payment payment = owned(subscription, orderId);
        if (!customerKey.equals(subscription.getCustomerKey())) {
            throw new SubscriptionCheckoutException("결제 인증 정보가 일치하지 않습니다.");
        }
        boolean claimed = payment.getStatus() == PaymentStatus.READY;
        if (claimed) {
            if (subscription.effectivePlan(Instant.now()) == SubscriptionPlan.PRO) {
                throw new SubscriptionCheckoutException("이미 PRO 구독 중입니다.");
            }
            payment.startProcessing();
        }
        return new Claim(claimed, response(payment, subscription));
    }

    public void storeBillingKey(Long userId, String orderId, String ciphertext) {
        Subscription subscription = lock(userId);
        Payment payment = owned(subscription, orderId);
        if (payment.getStatus() != PaymentStatus.PROCESSING) throw new IllegalStateException("Invalid checkout state");
        subscription.storeBillingKey(ciphertext);
    }

    public CheckoutResponse succeed(Long userId, String orderId, PaymentApprovalResult approval) {
        Subscription subscription = lock(userId);
        Payment payment = owned(subscription, orderId);
        if (payment.getStatus() != PaymentStatus.PROCESSING) throw new IllegalStateException("Invalid checkout state");
        if (!orderId.equals(approval.orderId()) || payment.getAmount() != approval.amount()) {
            throw new IllegalStateException("Approval does not match checkout");
        }
        payment.markSucceeded(approval.paymentKey(), approval.approvedAt());
        Instant end = BillingPeriods.oneMonthAfter(approval.approvedAt());
        subscription.activatePaidPro(approval.approvedAt(), end);
        return response(payment, subscription);
    }

    public CheckoutResponse fail(Long userId, String orderId, String code, boolean uncertain) {
        Subscription subscription = lock(userId);
        Payment payment = owned(subscription, orderId);
        if (payment.getStatus() == PaymentStatus.PROCESSING) {
            if (uncertain) payment.markUnknown();
            else payment.markFailed(code, Instant.now());
        }
        return response(payment, subscription);
    }

    public CheckoutResponse status(Long userId, String orderId) {
        Subscription subscription = lock(userId);
        return response(owned(subscription, orderId), subscription);
    }

    private Subscription lock(Long userId) {
        return subscriptions.findByUserIdForUpdate(userId).orElseThrow(UserNotFoundException::new);
    }

    private Payment owned(Subscription subscription, String orderId) {
        Payment payment = payments.findByOrderId(orderId)
                .orElseThrow(() -> new SubscriptionCheckoutException("결제 요청을 찾을 수 없습니다."));
        if (payment.getSubscription() == null || !payment.getSubscription().getId().equals(subscription.getId())
                || payment.getType() != PaymentType.INITIAL) {
            throw new SubscriptionCheckoutException("결제 요청을 찾을 수 없습니다.");
        }
        return payment;
    }

    private CheckoutResponse response(Payment p, Subscription s) {
        return new CheckoutResponse(p.getOrderId(), s.getCustomerKey(), p.getOrderName(), p.getAmount(),
                p.getStatus(), s.getCurrentPeriodEndAt());
    }

    public record Claim(boolean claimed, CheckoutResponse checkout) { }
}
