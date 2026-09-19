package com.kdh.solvego.domain.subscription.billing;

import com.kdh.solvego.domain.payment.exception.PaymentGatewayException;
import java.util.Set;

final class PaymentFailurePolicy {
    private static final Set<String> DECLINED = Set.of(
            "REJECT_CARD_COMPANY",
            "EXCEED_MAX_CARD_INSTALLMENT_PLAN",
            "NOT_SUPPORTED_INSTALLMENT_PLAN_CARD_OR_MERCHANT",
            "INVALID_CARD_EXPIRATION",
            "INVALID_CARD_NUMBER",
            "NOT_MATCHES_CUSTOMER_KEY",
            "INVALID_BILL_KEY",
            "EXCEED_MAX_PAYMENT_AMOUNT",
            "EXCEED_MAX_DAILY_PAYMENT_COUNT",
            "REJECT_CARD_PAYMENT"
    );

    private PaymentFailurePolicy() { }

    static boolean isDefinitive(PaymentGatewayException exception, boolean chargeStarted) {
        Integer status = exception.getHttpStatus();
        String code = exception.getFailureCode();
        return exception.getReason() == PaymentGatewayException.Reason.HTTP_ERROR
                && status != null
                && status >= 400
                && status < 500
                && code != null
                && (!chargeStarted || DECLINED.contains(code));
    }
}
