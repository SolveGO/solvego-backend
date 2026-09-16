package com.kdh.solvego.domain.payment.gateway.dto;

public record BillingKeyResult(String billingKey, String customerKey) {
    @Override
    public String toString() {
        return "BillingKeyResult[REDACTED]";
    }
}
