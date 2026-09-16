package com.kdh.solvego.domain.payment.gateway;

import com.kdh.solvego.domain.payment.gateway.dto.BillingKeyResult;
import com.kdh.solvego.domain.payment.gateway.dto.PaymentApprovalResult;

/** External payment communication only; callers own persistence and subscription changes. */
public interface PaymentGateway {
    BillingKeyResult issueBillingKey(String authKey, String customerKey);

    /** No automatic retries: a communication failure can leave the payment outcome unknown. */
    PaymentApprovalResult charge(String billingKey, String customerKey,
                                 String orderId, String orderName, long amount);
}
