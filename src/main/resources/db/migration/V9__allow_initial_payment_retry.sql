ALTER TABLE payments
    DROP INDEX uk_payments_initial_subscription,
    DROP COLUMN initial_payment_marker;
