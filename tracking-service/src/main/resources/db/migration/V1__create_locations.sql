CREATE TABLE locations (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    shipment_id BIGINT        NOT NULL,
    driver_id   BIGINT        NOT NULL,
    latitude    DECIMAL(9, 6) NOT NULL,
    longitude   DECIMAL(9, 6) NOT NULL,
    -- When the driver's device took the reading (may be slightly in the past if it was offline).
    recorded_at DATETIME(6)   NOT NULL,
    -- When we received it.
    created_at  DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT chk_locations_lat CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT chk_locations_lng CHECK (longitude BETWEEN -180 AND 180),
    -- "Latest location" and "route" are both served by this index.
    INDEX idx_locations_shipment_time (shipment_id, recorded_at DESC, id DESC)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
