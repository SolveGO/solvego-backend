package com.kdh.solvego.domain.subscription.entity;

import com.kdh.solvego.domain.subscription.type.SubscriptionPlan;
import com.kdh.solvego.domain.subscription.type.SubscriptionStatus;
import com.kdh.solvego.domain.user.entity.User;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(
        name = "subscriptions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_subscriptions_user",
                columnNames = "user_id"
        )
)
public class Subscription {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubscriptionPlan plan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubscriptionStatus status;

    @Column(name = "customer_key", unique = true, length = 50)
    private String customerKey;

    @Column(name = "billing_key_ciphertext", length = 512)
    private String billingKeyCiphertext;

    @Column(name = "current_period_start_at")
    private Instant currentPeriodStartAt;

    @Column(name = "current_period_end_at")
    private Instant currentPeriodEndAt;

    @Column(name = "next_billing_at")
    private Instant nextBillingAt;

    @Column(name = "auto_renew", nullable = false)
    private boolean autoRenew;

    @Column(name = "cancel_at_period_end", nullable = false)
    private boolean cancelAtPeriodEnd;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Subscription() {
    }

    private Subscription(User user) {
        this.user = user;
        this.plan = SubscriptionPlan.FREE;
        this.status = SubscriptionStatus.INACTIVE;
        this.autoRenew = false;
        this.cancelAtPeriodEnd = false;
    }

    public static Subscription free(User user) {
        return new Subscription(user);
    }

    public SubscriptionPlan effectivePlan(Instant now) {
        if (
                plan == SubscriptionPlan.PRO &&
                status == SubscriptionStatus.ACTIVE &&
                (currentPeriodStartAt == null || !currentPeriodStartAt.isAfter(now)) &&
                (currentPeriodEndAt == null || currentPeriodEndAt.isAfter(now))
        ) {
            return SubscriptionPlan.PRO;
        }
        return SubscriptionPlan.FREE;
    }

    public void activatePro(Instant periodStartAt, Instant periodEndAt) {
        this.plan = SubscriptionPlan.PRO;
        this.status = SubscriptionStatus.ACTIVE;
        this.currentPeriodStartAt = periodStartAt;
        this.currentPeriodEndAt = periodEndAt;
    }

    public String getCustomerKey() { return customerKey; }
    public String getBillingKeyCiphertext() { return billingKeyCiphertext; }
    public Instant getCurrentPeriodStartAt() { return currentPeriodStartAt; }
    public Instant getCurrentPeriodEndAt() { return currentPeriodEndAt; }
    public Instant getNextBillingAt() { return nextBillingAt; }
    public boolean isAutoRenew() { return autoRenew; }

    public void initializeCustomerKey(String key) {
        if (customerKey == null) customerKey = key;
    }

    public void storeBillingKey(String ciphertext) { billingKeyCiphertext = ciphertext; }

    public void activatePaidPro(Instant start, Instant end) {
        activatePro(start, end);
        nextBillingAt = end;
        autoRenew = true;
        cancelAtPeriodEnd = false;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public SubscriptionPlan getPlan() {
        return plan;
    }

    public SubscriptionStatus getStatus() {
        return status;
    }
}
