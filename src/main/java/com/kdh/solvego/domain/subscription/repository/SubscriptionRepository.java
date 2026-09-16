package com.kdh.solvego.domain.subscription.repository;

import com.kdh.solvego.domain.subscription.entity.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select s from Subscription s where s.user.id = :userId")
    Optional<Subscription> findByUserIdForUpdate(@org.springframework.data.repository.query.Param("userId") Long userId);

    Optional<Subscription> findByUserId(Long userId);
}
