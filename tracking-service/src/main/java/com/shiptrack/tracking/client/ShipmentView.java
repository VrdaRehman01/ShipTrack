package com.shiptrack.tracking.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

/** The few fields tracking needs from shipment-service's GET /api/v1/shipments/{id}. */
@Serdeable
@JsonIgnoreProperties(ignoreUnknown = true)
public record ShipmentView(Long id, String trackingCode, String status, Long customerId, @Nullable Long driverId) {

    /** Locations make sense only while the parcel is on the move. */
    public boolean isInProgress() {
        return "PICKED_UP".equals(status) || "IN_TRANSIT".equals(status);
    }
}
