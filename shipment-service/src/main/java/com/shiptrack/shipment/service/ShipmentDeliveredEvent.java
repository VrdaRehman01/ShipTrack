package com.shiptrack.shipment.service;

/** Published when a shipment becomes DELIVERED; handled after the transaction commits. */
public record ShipmentDeliveredEvent(Long shipmentId) {
}
