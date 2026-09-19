package com.kdh.solvego.domain.subscription.billing;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "payment.renewal.scheduler-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class RenewalScheduler {
    private final RenewalSubscriptionService service;

    public RenewalScheduler(RenewalSubscriptionService service) {
        this.service = service;
    }

    @Scheduled(
            fixedDelayString = "${payment.renewal.fixed-delay-ms:60000}",
            initialDelayString = "${payment.renewal.initial-delay-ms:60000}"
    )
    public void renewDueSubscriptions() {
        service.renewDueSubscriptions();
    }
}
