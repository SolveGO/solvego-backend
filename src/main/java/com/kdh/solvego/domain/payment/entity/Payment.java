package com.kdh.solvego.domain.payment.entity;

import com.kdh.solvego.domain.payment.type.PaymentStatus;
import com.kdh.solvego.domain.payment.type.PaymentType;
import com.kdh.solvego.domain.subscription.entity.Subscription;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "payments")
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id")
    private Subscription subscription;

    @Column(name = "order_id", nullable = false, unique = true, length = 64)
    private String orderId;

    @Column(name = "payment_key", unique = true, length = 200)
    private String paymentKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(nullable = false)
    private long amount;

    @Column(name = "order_name", nullable = false, length = 100)
    private String orderName = "SolveGO PRO 1개월";

    @Column(name = "billing_cycle_at")
    private Instant billingCycleAt;

    @Column(name = "failure_code", length = 100)
    private String failureCode;

    @CreationTimestamp
    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "failed_at")
    private Instant failedAt;

    protected Payment() {
    }

    public Payment(
            Subscription subscription,
            String orderId,
            PaymentType type,
            long amount
    ) {
        this.subscription = subscription;
        this.orderId = orderId;
        this.type = type;
        this.status = PaymentStatus.READY;
        this.amount = amount;
    }

    public static Payment renewal(
            Subscription subscription,
            String orderId,
            long amount,
            Instant billingCycleAt
    ) {
        Payment payment = new Payment(subscription, orderId, PaymentType.RENEWAL, amount);
        payment.billingCycleAt = billingCycleAt;
        return payment;
    }

    public Long getId() {
        return id;
    }

    public Subscription getSubscription() {
        return subscription;
    }

    public String getOrderId() {
        return orderId;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public PaymentType getType() {
        return type;
    }

    public long getAmount() {
        return amount;
    }

    public String getOrderName() { return orderName; }
    public String getPaymentKey() { return paymentKey; }
    public Instant getApprovedAt() { return approvedAt; }
    public String getFailureCode() { return failureCode; }
    public Instant getFailedAt() { return failedAt; }
    public Instant getBillingCycleAt() { return billingCycleAt; }

    public void startProcessing() {
        if (status != PaymentStatus.READY) throw new IllegalStateException("Payment already claimed");
        status = PaymentStatus.PROCESSING;
    }

    public void markUnknown() {
        if (status == PaymentStatus.PROCESSING) status = PaymentStatus.UNKNOWN;
    }

    public void markSucceeded(String paymentKey, Instant approvedAt) {
        this.paymentKey = paymentKey;
        this.status = PaymentStatus.SUCCEEDED;
        this.approvedAt = approvedAt;
        this.failureCode = null;
        this.failedAt = null;
    }

    public void markFailed(String failureCode, Instant failedAt) {
        this.status = PaymentStatus.FAILED;
        this.failureCode = failureCode;
        this.failedAt = failedAt;
    }

    public void markRefunded() {
        this.status = PaymentStatus.REFUNDED;
    }
}
