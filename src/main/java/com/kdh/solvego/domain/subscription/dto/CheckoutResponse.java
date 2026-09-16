package com.kdh.solvego.domain.subscription.dto;

import com.kdh.solvego.domain.payment.type.PaymentStatus;
import java.time.Instant;

public record CheckoutResponse(String orderId, String customerKey, String orderName, long amount,
                               PaymentStatus status, Instant currentPeriodEndAt) {
    @Override public String toString() { return "CheckoutResponse[" + status + "]"; }
}
