CREATE TABLE shipments (
    id                      BIGINT        NOT NULL AUTO_INCREMENT,
    -- Public code like ST-2026-000042; set right after insert (derived from id), hence nullable.
    tracking_code           VARCHAR(20)   NULL,
    customer_id             BIGINT        NOT NULL,
    driver_id               BIGINT        NULL,
    sender_name             VARCHAR(100)  NOT NULL,
    sender_phone            VARCHAR(20)   NOT NULL,
    pickup_address          VARCHAR(255)  NOT NULL,
    receiver_name           VARCHAR(100)  NOT NULL,
    receiver_phone          VARCHAR(20)   NOT NULL,
    delivery_address        VARCHAR(255)  NOT NULL,
    weight_kg               DECIMAL(8, 3) NOT NULL,
    description             VARCHAR(255)  NULL,
    status                  VARCHAR(20)   NOT NULL,
    -- FR-10: retries with the same Idempotency-Key return the first shipment.
    idempotency_key         VARCHAR(64)   NULL,
    idempotency_fingerprint CHAR(64)      NULL,
    -- FR-7: set once billing-service has the invoice (retried in the background if billing was down).
    invoiced                BOOLEAN       NOT NULL DEFAULT FALSE,
    delivered_at            DATETIME(6)   NULL,
    version                 BIGINT        NOT NULL DEFAULT 0,
    created_at              DATETIME(6)   NOT NULL,
    updated_at              DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_shipments_tracking_code UNIQUE (tracking_code),
    CONSTRAINT uk_shipments_idempotency UNIQUE (customer_id, idempotency_key),
    CONSTRAINT chk_shipments_status CHECK (status IN ('CREATED', 'PICKED_UP', 'IN_TRANSIT', 'DELIVERED', 'CANCELLED')),
    CONSTRAINT chk_shipments_weight CHECK (weight_kg > 0),
    INDEX idx_shipments_customer (customer_id, created_at),
    INDEX idx_shipments_driver (driver_id, created_at),
    INDEX idx_shipments_status (status, created_at),
    INDEX idx_shipments_uninvoiced (status, invoiced)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

-- Every status change, so customers see a timeline and admins have an audit trail.
CREATE TABLE shipment_status_changes (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    shipment_id     BIGINT       NOT NULL,
    from_status     VARCHAR(20)  NULL,
    to_status       VARCHAR(20)  NOT NULL,
    changed_by      BIGINT       NOT NULL,
    changed_by_role VARCHAR(20)  NOT NULL,
    note            VARCHAR(255) NULL,
    changed_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_status_changes_shipment FOREIGN KEY (shipment_id) REFERENCES shipments (id) ON DELETE CASCADE,
    INDEX idx_status_changes_shipment (shipment_id, changed_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
