package com.shiptrack.shipment.controller;

import com.shiptrack.common.error.ApiError;
import com.shiptrack.common.security.AuthenticatedUser;
import com.shiptrack.common.web.PageResponse;
import com.shiptrack.shipment.dto.AssignDriverRequest;
import com.shiptrack.shipment.dto.CreateShipmentRequest;
import com.shiptrack.shipment.dto.ShipmentResponse;
import com.shiptrack.shipment.dto.StatusChangeResponse;
import com.shiptrack.shipment.dto.UpdateStatusRequest;
import com.shiptrack.shipment.entity.ShipmentStatus;
import com.shiptrack.shipment.service.ShipmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** REST only: every rule lives in ShipmentService. */
@RestController
@RequestMapping("/api/v1/shipments")
@Tag(name = "Shipments", description = "Create shipments and move them through delivery")
@ApiResponse(responseCode = "401", description = "Missing or expired JWT",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
public class ShipmentController {

    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    public static final String IDEMPOTENT_REPLAYED = "Idempotent-Replayed";

    private final ShipmentService shipmentService;

    public ShipmentController(ShipmentService shipmentService) {
        this.shipmentService = shipmentService;
    }

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Create a shipment (customer)",
            description = "Send an Idempotency-Key (e.g. a UUID) to make retries safe: repeating the same request "
                    + "returns the first shipment with 200 and Idempotent-Replayed: true instead of a duplicate.")
    @ApiResponse(responseCode = "201", description = "Shipment created")
    @ApiResponse(responseCode = "200", description = "Replay of an earlier request with the same Idempotency-Key")
    @ApiResponse(responseCode = "400", description = "Validation failed",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "422", description = "Idempotency-Key reused for a different request",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ResponseEntity<ShipmentResponse> create(
            @AuthenticationPrincipal AuthenticatedUser caller,
            @Parameter(description = "Unique per new shipment, 1-64 chars", example = "3f9c2b1e-7a4d-4c1f-9e2b-1a2b3c4d5e6f")
            @RequestHeader(name = IDEMPOTENCY_KEY, required = false) @Pattern(regexp = "^[A-Za-z0-9_-]{1,64}$") String idempotencyKey,
            @Valid @RequestBody CreateShipmentRequest request) {
        ShipmentService.CreateResult result = shipmentService.create(caller, request, idempotencyKey);
        ShipmentResponse body = result.shipment();
        if (result.replayed()) {
            return ResponseEntity.status(HttpStatus.OK).header(IDEMPOTENT_REPLAYED, "true").body(body);
        }
        return ResponseEntity.created(URI.create("/api/v1/shipments/" + body.id())).body(body);
    }

    @GetMapping
    @Operation(summary = "List shipments, newest first",
            description = "Customers get their own, drivers get the ones assigned to them, admins get all.")
    public PageResponse<ShipmentResponse> list(
            @AuthenticationPrincipal AuthenticatedUser caller,
            @Parameter(example = "IN_TRANSIT") @RequestParam(required = false) ShipmentStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(shipmentService.list(caller, status, page, size), s -> s);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Shipment details (owner, assigned driver or admin)")
    @ApiResponse(responseCode = "403", description = "Not your shipment",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "No such shipment",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ShipmentResponse get(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable Long id) {
        return shipmentService.get(caller, id);
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "Status timeline: who changed what, and when")
    public List<StatusChangeResponse> history(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable Long id) {
        return shipmentService.history(caller, id);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Move to the next status",
            description = "CREATED → PICKED_UP → IN_TRANSIT → DELIVERED, one step at a time. Drivers (assigned) do the "
                    + "delivery steps; customers may cancel before pickup; admins may do any valid move. "
                    + "DELIVERED triggers the invoice automatically.")
    @ApiResponse(responseCode = "400", description = "INVALID_STATUS_TRANSITION or DRIVER_NOT_ASSIGNED",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "Your role can't make this change",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "Changed by someone else at the same time",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ShipmentResponse updateStatus(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable Long id,
                                         @Valid @RequestBody UpdateStatusRequest request) {
        return shipmentService.updateStatus(caller, id, request);
    }

    @PatchMapping("/{id}/driver")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Assign or reassign the driver (admin)")
    @ApiResponse(responseCode = "400", description = "INVALID_DRIVER: no such user, or not a DRIVER",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "SHIPMENT_CLOSED: already delivered or cancelled",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ShipmentResponse assignDriver(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable Long id,
                                         @Valid @RequestBody AssignDriverRequest request) {
        return shipmentService.assignDriver(caller, id, request.driverId());
    }
}
