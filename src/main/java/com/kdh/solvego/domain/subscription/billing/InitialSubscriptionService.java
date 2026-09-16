package com.kdh.solvego.domain.subscription.billing;

import com.kdh.solvego.domain.payment.exception.PaymentGatewayException;
import com.kdh.solvego.domain.payment.gateway.PaymentGateway;
import com.kdh.solvego.domain.payment.gateway.dto.BillingKeyResult;
import com.kdh.solvego.domain.payment.gateway.dto.PaymentApprovalResult;
import com.kdh.solvego.domain.subscription.dto.CheckoutResponse;
import com.kdh.solvego.domain.subscription.dto.CompleteCheckoutRequest;
import com.kdh.solvego.domain.subscription.exception.SubscriptionCheckoutException;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class InitialSubscriptionService {
    // Only explicit rejections are final. Duplicate/timeout/server/malformed responses need reconciliation.
    private static final Set<String> DECLINED = Set.of("REJECT_CARD_COMPANY", "EXCEED_MAX_CARD_INSTALLMENT_PLAN",
            "NOT_SUPPORTED_INSTALLMENT_PLAN_CARD_OR_MERCHANT", "INVALID_CARD_EXPIRATION",
            "INVALID_CARD_NUMBER", "NOT_MATCHES_CUSTOMER_KEY", "INVALID_BILL_KEY",
            "EXCEED_MAX_PAYMENT_AMOUNT", "EXCEED_MAX_DAILY_PAYMENT_COUNT", "REJECT_CARD_PAYMENT");
    private final CheckoutTransactions transactions;
    private final PaymentGateway gateway;
    private final BillingKeyCipher cipher;
    private final long amount;
    private final boolean enabled;

    public InitialSubscriptionService(CheckoutTransactions transactions, PaymentGateway gateway,
            BillingKeyCipher cipher, @Value("${payment.pro-monthly-amount:5000}") long amount,
            @Value("${payment.toss.secret-key:}") String secretKey) {
        if (amount <= 0) throw new IllegalArgumentException("PRO amount must be positive");
        this.transactions = transactions;
        this.gateway = gateway;
        this.cipher = cipher;
        this.amount = amount;
        // This first-checkout release deliberately permits test API keys only.
        this.enabled = secretKey.startsWith("test_sk_") && cipher.isConfigured();
    }

    public CheckoutResponse prepare(Long userId) {
        requireConfigured();
        return transactions.prepare(userId, amount);
    }

    public CheckoutResponse complete(Long userId, String orderId, CompleteCheckoutRequest request) {
        requireConfigured();
        CheckoutTransactions.Claim claim = transactions.claim(userId, orderId, request.customerKey());
        if (!claim.claimed()) return claim.checkout();
        CheckoutResponse checkout = claim.checkout();
        boolean chargeStarted = false;
        try {
            BillingKeyResult billing = gateway.issueBillingKey(request.authKey(), checkout.customerKey());
            String ciphertext = cipher.encrypt(billing.billingKey(), checkout.customerKey());
            // Durable encrypted billing key before sending any monetary request.
            transactions.storeBillingKey(userId, orderId, ciphertext);
            chargeStarted = true;
            PaymentApprovalResult approval = gateway.charge(billing.billingKey(), checkout.customerKey(),
                    orderId, checkout.orderName(), checkout.amount());
            return transactions.succeed(userId, orderId, approval);
        } catch (PaymentGatewayException e) {
            boolean definitiveHttp = e.getReason() == PaymentGatewayException.Reason.HTTP_ERROR
                    && e.getHttpStatus() != null && e.getHttpStatus() >= 400 && e.getHttpStatus() < 500
                    && e.getFailureCode() != null
                    && (!chargeStarted || DECLINED.contains(e.getFailureCode()));
            return recordFailure(userId, orderId, definitiveHttp ? e.getFailureCode() : null, !definitiveHttp);
        } catch (RuntimeException e) {
            // Includes commit failure after Toss approval. Never attach exceptions that may contain secrets.
            return recordFailure(userId, orderId, chargeStarted ? null : "CHECKOUT_SETUP_FAILED", chargeStarted);
        }
    }

    public CheckoutResponse status(Long userId, String orderId) { return transactions.status(userId, orderId); }

    private CheckoutResponse recordFailure(Long userId, String orderId, String code, boolean uncertain) {
        try {
            return transactions.fail(userId, orderId, code, uncertain);
        } catch (RuntimeException e) {
            // The committed PROCESSING marker still blocks recharging, even during a total DB outage.
            throw new SubscriptionCheckoutException("결제 결과 확인이 필요합니다. 다시 결제하지 말고 주문 상태를 확인해주세요.");
        }
    }

    private void requireConfigured() {
        if (!enabled) throw new SubscriptionCheckoutException("테스트 결제 설정이 준비되지 않았습니다.");
    }
}
