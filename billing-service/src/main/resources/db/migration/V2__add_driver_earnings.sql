ALTER TABLE invoices
    ADD COLUMN driver_id BIGINT NULL,
    ADD COLUMN driver_payout_percent DECIMAL(5, 2) NULL,
    ADD COLUMN driver_payout_amount DECIMAL(12, 2) NULL,
    ADD INDEX idx_invoices_driver (driver_id, issued_at);
