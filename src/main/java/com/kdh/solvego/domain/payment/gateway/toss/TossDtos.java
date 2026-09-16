package com.kdh.solvego.domain.payment.gateway.toss;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.OffsetDateTime;

final class TossDtos {
    private TossDtos() { }

    record BillingRequest(String authKey, String customerKey) {
        @Override public String toString() { return "BillingRequest[REDACTED]"; }
    }

    record ChargeRequest(String customerKey, String orderId, String orderName, long amount) {
        @Override public String toString() { return "ChargeRequest[REDACTED]"; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record BillingResponse(String billingKey, String customerKey) {
        @Override public String toString() { return "BillingResponse[REDACTED]"; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ChargeResponse(String paymentKey, String orderId, Long totalAmount,
                          String status, OffsetDateTime approvedAt) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ErrorResponse(String code) { }
}
