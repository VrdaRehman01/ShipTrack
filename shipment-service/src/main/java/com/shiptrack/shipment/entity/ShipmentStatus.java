package com.shiptrack.shipment.entity;

import java.util.Set;

/**
 * The shipment lifecycle (FR-5). Forward one step at a time, no skipping:
 * CREATED -> PICKED_UP -> IN_TRANSIT -> DELIVERED. A shipment can be CANCELLED only before pickup.
 */
public enum ShipmentStatus {
    CREATED,
    PICKED_UP,
    IN_TRANSIT,
    DELIVERED,
    CANCELLED;

    public Set<ShipmentStatus> allowedNext() {
        return switch (this) {
            case CREATED -> Set.of(PICKED_UP, CANCELLED);
            case PICKED_UP -> Set.of(IN_TRANSIT);
            case IN_TRANSIT -> Set.of(DELIVERED);
            case DELIVERED, CANCELLED -> Set.of();
        };
    }

    public boolean canMoveTo(ShipmentStatus next) {
        return allowedNext().contains(next);
    }

    /** No more changes once delivered or cancelled. */
    public boolean isFinal() {
        return allowedNext().isEmpty();
    }

    /** Statuses a driver sets while doing the delivery. */
    public boolean isDriverStep() {
        return this == PICKED_UP || this == IN_TRANSIT || this == DELIVERED;
    }
}
