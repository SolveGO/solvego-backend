ALTER TABLE users
    ADD COLUMN subscription_plan VARCHAR(20) NOT NULL DEFAULT 'FREE',
    ADD COLUMN subscription_status VARCHAR(20) NOT NULL DEFAULT 'INACTIVE',
    ADD COLUMN subscription_started_at DATETIME(6) NULL,
    ADD COLUMN subscription_expires_at DATETIME(6) NULL;
