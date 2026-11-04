package com.shiptrack.shipment.service;

import com.shiptrack.common.error.ApiException;
import com.shiptrack.common.security.AuthenticatedUser;
import com.shiptrack.common.security.Role;
import com.shiptrack.shipment.client.AuthClient;
import com.shiptrack.shipment.dto.CreateShipmentRequest;
import com.shiptrack.shipment.dto.PublicTrackingResponse;
import com.shiptrack.shipment.dto.ShipmentResponse;
import com.shiptrack.shipment.dto.StatusChangeResponse;
import com.shiptrack.shipment.dto.UpdateStatusRequest;
import com.shiptrack.shipment.entity.Shipment;
import com.shiptrack.shipment.entity.ShipmentStatus;
import com.shiptrack.shipment.entity.ShipmentStatusChange;
import com.shiptrack.shipment.exception.ShipmentErrorCode;
import com.shiptrack.shipment.repository.ShipmentRepository;
import com.shiptrack.shipment.repository.ShipmentStatusChangeRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * All shipment business rules: who may see what (FR-6), the lifecycle (FR-5), idempotent create (FR-10)
 * and kicking off billing on delivery (FR-7).
 */
@Service
public class ShipmentService {

    /** Result of create: the shipment, and whether it was an idempotent replay of an earlier request. */
    public record CreateResult(ShipmentResponse shipment, boolean replayed) {
    }

    private final ShipmentRepository shipments;
    private final ShipmentStatusChangeRepository statusChanges;
    private final AuthClient authClient;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate tx;
    private final Clock clock;

    public ShipmentService(ShipmentRepository shipments, ShipmentStatusChangeRepository statusChanges, AuthClient authClient,
                           ApplicationEventPublisher events, TransactionTemplate tx, Clock clock) {
        this.shipments = shipments;
        this.statusChanges = statusChanges;
        this.authClient = authClient;
        this.events = events;
        this.tx = tx;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- create

    /**
     * Creates a shipment for the calling customer. With an Idempotency-Key, retries of the same request return
     * the first shipment instead of creating a duplicate; reusing a key for a different request is rejected.
     */
    public CreateResult create(AuthenticatedUser caller, CreateShipmentRequest request, String idempotencyKey) {
        String fingerprint = idempotencyKey == null ? null : fingerprint(request);
        if (idempotencyKey != null) {
            var existing = shipments.findByCustomerIdAndIdempotencyKey(caller.id(), idempotencyKey);
            if (existing.isPresent()) {
                return replay(existing.get(), fingerprint);
            }
        }
        try {
            Shipment created = tx.execute(status -> createInTransaction(caller, request, idempotencyKey, fingerprint));
            return new CreateResult(ShipmentResponse.from(Objects.requireNonNull(created)), false);
        } catch (DataIntegrityViolationException e) {
            // Two retries raced; the unique (customer_id, idempotency_key) index let exactly one win.
            if (idempotencyKey == null) {
                throw e;
            }
            Shipment winner = shipments.findByCustomerIdAndIdempotencyKey(caller.id(), idempotencyKey).orElseThrow(() -> e);
            return replay(winner, fingerprint);
        }
    }

    private Shipment createInTransaction(AuthenticatedUser caller, CreateShipmentRequest r, String key, String fingerprint) {
        Shipment shipment = new Shipment(caller.id(),
                r.senderName().trim(), r.senderPhone().trim(), r.pickupAddress().trim(),
                r.receiverName().trim(), r.receiverPhone().trim(), r.deliveryAddress().trim(),
                r.weightKg(), r.description() == null ? null : r.description().trim());
        if (key != null) {
            shipment.useIdempotencyKey(key, fingerprint);
        }
        shipments.saveAndFlush(shipment); // need the id for the tracking code
        shipment.assignTrackingCode(trackingCode(shipment.getId(), shipment.getCreatedAt()));
        statusChanges.save(new ShipmentStatusChange(
                shipment.getId(), null, ShipmentStatus.CREATED, caller.id(), caller.role(), null, clock.instant()));
        return shipment;
    }

    private CreateResult replay(Shipment existing, String fingerprint) {
        if (!Objects.equals(existing.getIdempotencyFingerprint(), fingerprint)) {
            throw new ApiException(ShipmentErrorCode.IDEMPOTENCY_KEY_REUSED,
                    "This Idempotency-Key was already used for a different shipment. Use a new key for a new shipment.");
        }
        return new CreateResult(ShipmentResponse.from(existing), true);
    }

    /** ST-2026-000042: year created + zero-padded id. Matches the format in the API design system. */
    static String trackingCode(Long id, Instant createdAt) {
        return "ST-%d-%06d".formatted(createdAt.atZone(ZoneOffset.UTC).getYear(), id);
    }

    /** SHA-256 of the meaningful request fields, so "same key, same body" can be told apart from "same key, new body". */
    static String fingerprint(CreateShipmentRequest r) {
        String canonical = Stream.of(r.senderName(), r.senderPhone(), r.pickupAddress(), r.receiverName(),
                        r.receiverPhone(), r.deliveryAddress(), r.weightKg().stripTrailingZeros().toPlainString(),
                        r.description())
                .map(v -> v == null ? "" : v.trim())
                .collect(Collectors.joining("\u0000"));
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }

    // ---------------------------------------------------------------- read

    /** Customers see their own shipments, drivers see the ones assigned to them, admins see everything. */
    @Transactional(readOnly = true)
    public Page<ShipmentResponse> list(AuthenticatedUser caller, ShipmentStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        Page<Shipment> result = switch (caller.role()) {
            case CUSTOMER -> status == null
                    ? shipments.findByCustomerId(caller.id(), pageable)
                    : shipments.findByCustomerIdAndStatus(caller.id(), status, pageable);
            case DRIVER -> status == null
                    ? shipments.findByDriverId(caller.id(), pageable)
                    : shipments.findByDriverIdAndStatus(caller.id(), status, pageable);
            case ADMIN -> status == null ? shipments.findAll(pageable) : shipments.findByStatus(status, pageable);
        };
        return result.map(ShipmentResponse::from);
    }

    @Transactional(readOnly = true)
    public ShipmentResponse get(AuthenticatedUser caller, Long id) {
        return ShipmentResponse.from(loadVisible(caller, id));
    }

    @Transactional(readOnly = true)
    public List<StatusChangeResponse> history(AuthenticatedUser caller, Long id) {
        loadVisible(caller, id);
        return statusChanges.findByShipmentIdOrderByIdAsc(id).stream().map(StatusChangeResponse::from).toList();
    }

    /** Anyone with the code can follow a parcel; the response carries no personal data. */
    @Transactional(readOnly = true)
    public PublicTrackingResponse track(String trackingCode) {
        Shipment shipment = shipments.findByTrackingCode(trackingCode.trim().toUpperCase())
                .orElseThrow(() -> ApiException.notFound("Shipment", trackingCode));
        return PublicTrackingResponse.from(shipment, statusChanges.findByShipmentIdOrderByIdAsc(shipment.getId()));
    }

    // ---------------------------------------------------------------- change

    /**
     * Moves a shipment one step along its lifecycle.
     * Drivers (assigned only) do pickup -> transit -> delivered, customers may only cancel before pickup,
     * admins may make any valid move. Invalid jumps give 400 INVALID_STATUS_TRANSITION.
     */
    @Transactional
    public ShipmentResponse updateStatus(AuthenticatedUser caller, Long id, UpdateStatusRequest request) {
        Shipment shipment = loadVisible(caller, id);
        ShipmentStatus target = request.status();

        checkRoleMayMoveTo(caller, target);
        if (request.expectedVersion() != null && request.expectedVersion() != shipment.getVersion()) {
            throw new ApiException(ShipmentErrorCode.VERSION_MISMATCH,
                    "Shipment changed since version " + request.expectedVersion() + " (now " + shipment.getVersion()
                            + "). Reload it and try again.");
        }
        ShipmentStatus from = shipment.getStatus();
        if (!from.canMoveTo(target)) {
            throw new ApiException(ShipmentErrorCode.INVALID_STATUS_TRANSITION,
                    "Cannot move shipment from " + from + " to " + target + allowedHint(from));
        }
        if (target == ShipmentStatus.PICKED_UP && shipment.getDriverId() == null) {
            throw new ApiException(ShipmentErrorCode.DRIVER_NOT_ASSIGNED, "Assign a driver before the shipment is picked up");
        }

        Instant now = clock.instant();
        shipment.moveTo(target, now);
        shipments.saveAndFlush(shipment); // flush now so a concurrent update fails here with 409
        statusChanges.save(new ShipmentStatusChange(id, from, target, caller.id(), caller.role(), blankToNull(request.note()), now));

        if (target == ShipmentStatus.DELIVERED) {
            // Handled after commit, off the request thread: billing being slow or down never blocks delivery.
            events.publishEvent(new ShipmentDeliveredEvent(id));
        }
        return ShipmentResponse.from(shipment);
    }

    /** Admin assigns (or reassigns) a driver until the shipment is delivered or cancelled. */
    public ShipmentResponse assignDriver(AuthenticatedUser caller, Long id, Long driverId) {
        Shipment current = shipments.findById(id).orElseThrow(() -> ApiException.notFound("Shipment", id));
        checkOpen(current);

        // Verify outside the transaction so we don't hold a DB connection during an HTTP call.
        AuthClient.UserView driver = authClient.findUser(driverId)
                .orElseThrow(() -> new ApiException(ShipmentErrorCode.INVALID_DRIVER, "No user with id " + driverId));
        if (driver.role() != Role.DRIVER) {
            throw new ApiException(ShipmentErrorCode.INVALID_DRIVER,
                    "User " + driverId + " is a " + driver.role() + ", not a DRIVER");
        }

        return tx.execute(status -> {
            Shipment shipment = shipments.findById(id).orElseThrow(() -> ApiException.notFound("Shipment", id));
            checkOpen(shipment);
            shipment.assignDriver(driverId);
            shipments.saveAndFlush(shipment);
            return ShipmentResponse.from(shipment);
        });
    }

    // ---------------------------------------------------------------- rules

    private Shipment loadVisible(AuthenticatedUser caller, Long id) {
        Shipment shipment = shipments.findById(id).orElseThrow(() -> ApiException.notFound("Shipment", id));
        if (!canView(caller, shipment)) {
            throw ApiException.forbidden("You can only access your own shipments");
        }
        return shipment;
    }

    /** FR-6: owner, the assigned driver, or an admin. */
    static boolean canView(AuthenticatedUser caller, Shipment shipment) {
        return switch (caller.role()) {
            case ADMIN -> true;
            case CUSTOMER -> shipment.isOwnedBy(caller.id());
            case DRIVER -> shipment.isAssignedTo(caller.id());
        };
    }

    private static void checkRoleMayMoveTo(AuthenticatedUser caller, ShipmentStatus target) {
        switch (caller.role()) {
            case ADMIN -> {
                // any valid transition
            }
            case DRIVER -> {
                if (!target.isDriverStep()) {
                    throw ApiException.forbidden("Drivers can move a shipment to PICKED_UP, IN_TRANSIT or DELIVERED only");
                }
            }
            case CUSTOMER -> {
                if (target != ShipmentStatus.CANCELLED) {
                    throw ApiException.forbidden("Customers can only cancel a shipment");
                }
            }
            default -> throw ApiException.forbidden("Not allowed");
        }
    }

    private static void checkOpen(Shipment shipment) {
        if (shipment.getStatus().isFinal()) {
            throw new ApiException(ShipmentErrorCode.SHIPMENT_CLOSED,
                    "Shipment is " + shipment.getStatus() + " and can no longer be changed");
        }
    }

    private static String allowedHint(ShipmentStatus from) {
        if (from.isFinal()) {
            return " (" + from + " is final)";
        }
        return " (next allowed: " + from.allowedNext().stream().map(Enum::name).sorted().collect(Collectors.joining(", ")) + ")";
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
