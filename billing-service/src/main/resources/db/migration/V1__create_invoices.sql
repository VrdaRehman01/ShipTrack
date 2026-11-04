CREATE TABLE invoices (
    id             BIGINT         NOT NULL AUTO_INCREMENT,
    -- Public number like INV-2026-000042; set right after insert (derived from id), hence nullable.
    invoice_number VARCHAR(20)    NULL,
    -- One invoice per shipment: this unique key makes "create invoice" safe to retry.
    shipment_id    BIGINT         NOT NULL,
    tracking_code  VARCHAR(20)    NOT NULL,
    customer_id    BIGINT         NOT NULL,
    weight_kg      DECIMAL(8, 3)  NOT NULL,
    base_fee       DECIMAL(12, 2) NOT NULL,
    weight_charge  DECIMAL(12, 2) NOT NULL,
    amount         DECIMAL(12, 2) NOT NULL,
    currency       CHAR(3)        NOT NULL,
    status         VARCHAR(20)    NOT NULL,
    issued_at      DATETIME(6)    NOT NULL,
    paid_at        DATETIME(6)    NULL,
    version        BIGINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_invoices_number UNIQUE (invoice_number),
    CONSTRAINT uk_invoices_shipment UNIQUE (shipment_id),
    CONSTRAINT chk_invoices_status CHECK (status IN ('ISSUED', 'PAID')),
    CONSTRAINT chk_invoices_amount CHECK (amount >= 0),
    INDEX idx_invoices_customer (customer_id, issued_at),
    INDEX idx_invoices_status (status, issued_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
