package com.kdh.solvego.domain.payment.gateway;

import com.kdh.solvego.domain.payment.gateway.dto.BillingKeyResult;
import com.kdh.solvego.domain.payment.gateway.dto.PaymentApprovalResult;

/**
 * Communicates with the external payment provider.
 * Persistence and subscription state changes are handled by callers.
 */
public interface PaymentGateway {
    BillingKeyResult issueBillingKey(String authKey, String customerKey);

    /**
     * Does not retry automatically because a communication failure
     * does not necessarily mean the payment failed.
     */
    PaymentApprovalResult charge(String billingKey, String customerKey,
                                 String orderId, String orderName, long amount);
}
