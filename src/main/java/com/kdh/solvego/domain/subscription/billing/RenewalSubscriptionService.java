package com.kdh.solvego.domain.subscription.billing;

import com.kdh.solvego.domain.payment.exception.PaymentGatewayException;
import com.kdh.solvego.domain.payment.gateway.PaymentGateway;
import com.kdh.solvego.domain.payment.gateway.dto.PaymentApprovalResult;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class RenewalSubscriptionService {
    private static final Logger log = LoggerFactory.getLogger(RenewalSubscriptionService.class);

    private final RenewalTransactions transactions;
    private final PaymentGateway gateway;
    private final BillingKeyCipher cipher;
    private final long amount;
    private final int batchSize;
    private final boolean configured;
    private final Clock clock;

    @Autowired
    public RenewalSubscriptionService(
            RenewalTransactions transactions,
            PaymentGateway gateway,
            BillingKeyCipher cipher,
            @Value("${payment.pro-monthly-amount:5000}") long amount,
            @Value("${payment.renewal.batch-size:100}") int batchSize,
            @Value("${payment.toss.secret-key:}") String secretKey
    ) {
        this(transactions, gateway, cipher, amount, batchSize, secretKey, Clock.systemUTC());
    }

    RenewalSubscriptionService(
            RenewalTransactions transactions,
            PaymentGateway gateway,
            BillingKeyCipher cipher,
            long amount,
            int batchSize,
            String secretKey,
            Clock clock
    ) {
        if (amount <= 0) throw new IllegalArgumentException("PRO amount must be positive");
        if (batchSize <= 0) throw new IllegalArgumentException("Renewal batch size must be positive");
        this.transactions = transactions;
        this.gateway = gateway;
        this.cipher = cipher;
        this.amount = amount;
        this.batchSize = batchSize;
        this.configured = secretKey.startsWith("test_sk_") && cipher.isConfigured();
        this.clock = clock;
    }

    public int renewDueSubscriptions() {
        if (!configured) return 0;

        Instant now = Instant.now(clock);
        int claimed = 0;
        for (Long subscriptionId : transactions.findDueCandidateIds(now, batchSize)) {
            try {
                if (renewOne(subscriptionId, now)) claimed++;
            } catch (RuntimeException exception) {
                log.warn("Renewal processing needs attention for subscription {}", subscriptionId);
            }
        }
        return claimed;
    }

    boolean renewOne(Long subscriptionId, Instant now) {
        RenewalTransactions.Claim claim = transactions.claim(subscriptionId, now, amount);
        if (!claim.claimed()) return false;

        String billingKey;
        try {
            billingKey = cipher.decrypt(claim.billingKeyCiphertext(), claim.customerKey());
        } catch (RuntimeException exception) {
            recordFailure(subscriptionId, claim.orderId(), "BILLING_KEY_DECRYPT_FAILED", false);
            return true;
        }

        try {
            PaymentApprovalResult approval = gateway.charge(
                    billingKey,
                    claim.customerKey(),
                    claim.orderId(),
                    claim.orderName(),
                    claim.amount()
            );
            transactions.succeed(subscriptionId, claim.orderId(), approval);
        } catch (PaymentGatewayException exception) {
            boolean definitive = PaymentFailurePolicy.isDefinitive(exception, true);
            recordFailure(
                    subscriptionId,
                    claim.orderId(),
                    definitive ? exception.getFailureCode() : null,
                    !definitive
            );
        } catch (RuntimeException exception) {
            recordFailure(subscriptionId, claim.orderId(), null, true);
        }
        return true;
    }

    private void recordFailure(Long subscriptionId, String orderId, String code, boolean uncertain) {
        try {
            transactions.fail(subscriptionId, orderId, code, uncertain);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Renewal result requires reconciliation");
        }
    }
}
