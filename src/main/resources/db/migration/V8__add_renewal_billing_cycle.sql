ALTER TABLE payments
    ADD COLUMN billing_cycle_at DATETIME(6) NULL,
    ADD CONSTRAINT uk_payments_renewal_cycle
        UNIQUE (subscription_id, type, billing_cycle_at);
