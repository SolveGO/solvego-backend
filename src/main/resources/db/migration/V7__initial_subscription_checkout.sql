ALTER TABLE payments
    ADD COLUMN order_name VARCHAR(100) NOT NULL DEFAULT 'SolveGO PRO 1개월',
    ADD COLUMN initial_payment_marker TINYINT GENERATED ALWAYS AS
        (CASE WHEN type = 'INITIAL' THEN 1 ELSE NULL END) STORED,
    ADD CONSTRAINT uk_payments_initial_subscription UNIQUE (subscription_id, initial_payment_marker);
