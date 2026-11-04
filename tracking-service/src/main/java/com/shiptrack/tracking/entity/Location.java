package com.shiptrack.tracking.entity;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import java.math.BigDecimal;
import java.time.Instant;

/** One position report from a driver for a shipment. Never updated. */
@MappedEntity("locations")
public record Location(
        @Id @GeneratedValue Long id,
        @MappedProperty("shipment_id") Long shipmentId,
        @MappedProperty("driver_id") Long driverId,
        BigDecimal latitude,
        BigDecimal longitude,
        @MappedProperty("recorded_at") Instant recordedAt,
        @MappedProperty("created_at") Instant createdAt) {

    public static Location of(Long shipmentId, Long driverId, BigDecimal latitude, BigDecimal longitude,
                              Instant recordedAt, Instant createdAt) {
        return new Location(null, shipmentId, driverId, latitude, longitude, recordedAt, createdAt);
    }
}
