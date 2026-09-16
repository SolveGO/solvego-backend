package com.kdh.solvego.domain.payment.gateway.dto;

import java.time.Instant;

public record PaymentApprovalResult(String paymentKey, String orderId, long amount, Instant approvedAt) {
}
