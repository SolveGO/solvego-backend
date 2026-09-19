package com.kdh.solvego.domain.subscription.billing;

import java.time.Instant;
import java.time.ZoneId;

final class BillingPeriods {
    private static final ZoneId BILLING_ZONE = ZoneId.of("Asia/Seoul");

    private BillingPeriods() { }

    static Instant oneMonthAfter(Instant start) {
        return start.atZone(BILLING_ZONE).plusMonths(1).toInstant();
    }
}
