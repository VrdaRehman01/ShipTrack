package com.shiptrack.tracking.service;

import com.shiptrack.tracking.client.ShipmentClient;
import com.shiptrack.tracking.client.ShipmentView;
import com.shiptrack.tracking.dto.LocationRequest;
import com.shiptrack.tracking.dto.LocationResponse;
import com.shiptrack.tracking.entity.Location;
import com.shiptrack.tracking.exception.TrackingException;
import com.shiptrack.tracking.repository.LocationRepository;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import jakarta.inject.Singleton;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * FR-8: drivers post locations, owners/admins read the latest. Who may see a shipment is decided by
 * shipment-service (we forward the caller's token), so the access rules live in exactly one place.
 */
@Singleton
public class TrackingService {

    static final Duration MAX_CLOCK_SKEW = Duration.ofMinutes(2);
    static final Duration MAX_AGE = Duration.ofHours(24);

    private final ShipmentClient shipmentClient;
    private final LocationRepository locations;
    private final Clock clock;

    public TrackingService(ShipmentClient shipmentClient, LocationRepository locations, Clock clock) {
        this.shipmentClient = shipmentClient;
        this.locations = locations;
        this.clock = clock;
    }

    public LocationResponse record(Long driverId, Long shipmentId, LocationRequest request, String authorization, String correlationId) {
        ShipmentView shipment = loadShipment(shipmentId, authorization, correlationId);
        if (shipment.driverId() == null || !shipment.driverId().equals(driverId)) {
            throw TrackingException.forbidden("Only the driver assigned to this shipment can post its location");
        }
        if (!shipment.isInProgress()) {
            throw new TrackingException(HttpStatus.CONFLICT, "SHIPMENT_NOT_IN_PROGRESS",
                    "Locations can be posted only while the shipment is PICKED_UP or IN_TRANSIT (it is " + shipment.status() + ")");
        }
        Instant now = clock.instant();
        Instant recordedAt = request.recordedAt() == null ? now : request.recordedAt();
        if (recordedAt.isAfter(now.plus(MAX_CLOCK_SKEW)) || recordedAt.isBefore(now.minus(MAX_AGE))) {
            throw new TrackingException(HttpStatus.BAD_REQUEST, "INVALID_RECORDED_AT",
                    "recordedAt must be within the last 24 hours and not in the future");
        }
        Location saved = locations.save(Location.of(shipmentId, driverId,
                request.latitude().setScale(6, RoundingMode.HALF_UP), request.longitude().setScale(6, RoundingMode.HALF_UP),
                recordedAt.truncatedTo(ChronoUnit.SECONDS), now.truncatedTo(ChronoUnit.SECONDS)));
        return LocationResponse.from(saved);
    }

    public LocationResponse latest(Long shipmentId, String authorization, String correlationId) {
        loadShipment(shipmentId, authorization, correlationId);
        return locations.findByShipmentId(shipmentId, newestFirst(1)).stream().findFirst()
                .map(LocationResponse::from)
                .orElseThrow(() -> new TrackingException(HttpStatus.NOT_FOUND, "NO_LOCATION_YET",
                        "No location has been posted for shipment " + shipmentId + " yet"));
    }

    public List<LocationResponse> route(Long shipmentId, int limit, String authorization, String correlationId) {
        loadShipment(shipmentId, authorization, correlationId);
        return locations.findByShipmentId(shipmentId, newestFirst(limit)).stream().map(LocationResponse::from).toList();
    }

    /** Newest reading first; ties on recordedAt broken by insert order. */
    private static Pageable newestFirst(int limit) {
        return Pageable.from(0, limit, Sort.of(Sort.Order.desc("recordedAt"), Sort.Order.desc("id")));
    }

    /** Asks shipment-service, as the caller, whether they may see this shipment. */
    ShipmentView loadShipment(Long shipmentId, String authorization, String correlationId) {
        try {
            ShipmentView shipment = shipmentClient.getShipment(shipmentId, authorization, correlationId);
            if (shipment == null) {
                throw TrackingException.notFound("Shipment not found: " + shipmentId);
            }
            return shipment;
        } catch (HttpClientResponseException e) {
            throw switch (e.getStatus()) {
                case FORBIDDEN -> TrackingException.forbidden("You can only track shipments you own, drive or administer");
                case NOT_FOUND -> TrackingException.notFound("Shipment not found: " + shipmentId);
                case UNAUTHORIZED -> new TrackingException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Missing, invalid or expired token");
                default -> unavailable(e);
            };
        } catch (HttpClientException e) {
            throw unavailable(e);
        }
    }

    private static TrackingException unavailable(Exception cause) {
        return new TrackingException(HttpStatus.SERVICE_UNAVAILABLE, "DEPENDENCY_UNAVAILABLE",
                "shipment-service is unavailable right now. Please try again shortly.", cause);
    }
}
