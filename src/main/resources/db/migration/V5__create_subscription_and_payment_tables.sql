CREATE TABLE subscriptions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    plan VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    customer_key VARCHAR(50) NULL,
    billing_key_ciphertext VARCHAR(512) NULL,
    current_period_start_at DATETIME(6) NULL,
    current_period_end_at DATETIME(6) NULL,
    next_billing_at DATETIME(6) NULL,
    auto_renew BOOLEAN NOT NULL DEFAULT FALSE,
    cancel_at_period_end BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_subscriptions_user UNIQUE (user_id),
    CONSTRAINT uk_subscriptions_customer_key UNIQUE (customer_key),
    CONSTRAINT fk_subscriptions_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_subscriptions_billing (status, auto_renew, next_billing_at)
);

INSERT INTO subscriptions (
    user_id,
    plan,
    status,
    current_period_start_at,
    current_period_end_at,
    auto_renew,
    cancel_at_period_end,
    created_at,
    updated_at
)
SELECT
    id,
    subscription_plan,
    subscription_status,
    subscription_started_at,
    subscription_expires_at,
    FALSE,
    FALSE,
    COALESCE(created_at, CURRENT_TIMESTAMP(6)),
    CURRENT_TIMESTAMP(6)
FROM users;

CREATE TABLE payments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    subscription_id BIGINT NULL,
    order_id VARCHAR(64) NOT NULL,
    payment_key VARCHAR(200) NULL,
    type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    amount BIGINT NOT NULL,
    failure_code VARCHAR(100) NULL,
    requested_at DATETIME(6) NOT NULL,
    approved_at DATETIME(6) NULL,
    failed_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_payments_order_id UNIQUE (order_id),
    CONSTRAINT uk_payments_payment_key UNIQUE (payment_key),
    CONSTRAINT fk_payments_subscription
        FOREIGN KEY (subscription_id) REFERENCES subscriptions (id)
        ON DELETE SET NULL,
    INDEX idx_payments_subscription_requested (subscription_id, requested_at),
    INDEX idx_payments_status_requested (status, requested_at)
);
