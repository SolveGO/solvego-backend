package com.kdh.solvego.domain.user.entity;

import com.kdh.solvego.domain.user.type.SubscriptionPlan;
import com.kdh.solvego.domain.user.type.SubscriptionStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Table(name="users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable=false,length=50,unique = true)
    private String username;

    @Column(nullable = false)
    private String password;

    @CreationTimestamp
    @Column(name = "created_at",nullable = false,updatable = false)
    private LocalDateTime createdAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_plan", nullable = false, length = 20)
    private SubscriptionPlan subscriptionPlan = SubscriptionPlan.FREE;

    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_status", nullable = false, length = 20)
    private SubscriptionStatus subscriptionStatus = SubscriptionStatus.INACTIVE;

    @Column(name = "subscription_started_at")
    private Instant subscriptionStartedAt;

    @Column(name = "subscription_expires_at")
    private Instant subscriptionExpiresAt;

    protected User() {
    }

    public User(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public SubscriptionPlan getCurrentPlan(Instant now) {
        if (
                subscriptionPlan == SubscriptionPlan.PRO &&
                subscriptionStatus == SubscriptionStatus.ACTIVE &&
                (subscriptionStartedAt == null || !subscriptionStartedAt.isAfter(now)) &&
                (subscriptionExpiresAt == null || subscriptionExpiresAt.isAfter(now))
        ) {
            return SubscriptionPlan.PRO;
        }
        return SubscriptionPlan.FREE;
    }

    public void activateSubscription(
            SubscriptionPlan plan,
            Instant startedAt,
            Instant expiresAt
    ) {
        this.subscriptionPlan = plan;
        this.subscriptionStatus = SubscriptionStatus.ACTIVE;
        this.subscriptionStartedAt = startedAt;
        this.subscriptionExpiresAt = expiresAt;
    }

    public void changePassword(String password) {
        this.password = password;
    }

    public boolean matchesPassword(
            String rawPassword,
            PasswordEncoder passwordEncoder
    ) {
        return passwordEncoder.matches(rawPassword, this.password);
    }
}
