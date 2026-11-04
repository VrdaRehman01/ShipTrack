package com.shiptrack.tracking.client;

import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.client.annotation.Client;

/**
 * Declarative HTTP client for shipment-service. We forward the caller's own JWT, so shipment-service
 * decides who may see the shipment (owner, assigned driver, admin): one source of truth for FR-6.
 */
@Client(id = "shipment-service")
public interface ShipmentClient {

    @Get("/api/v1/shipments/{id}")
    ShipmentView getShipment(Long id,
                             @Header("Authorization") String authorization,
                             @Header("X-Correlation-Id") String correlationId);
}
