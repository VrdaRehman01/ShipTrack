package com.shiptrack.shipment.controller;

import com.shiptrack.common.error.ApiError;
import com.shiptrack.shipment.dto.PublicTrackingResponse;
import com.shiptrack.shipment.service.ShipmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Lets a parcel's receiver follow it with just the tracking code, without an account. */
@RestController
@RequestMapping("/api/v1/public/shipments")
@Tag(name = "Public tracking", description = "Follow a parcel by tracking code, no login")
@SecurityRequirements
public class PublicTrackingController {

    private final ShipmentService shipmentService;

    public PublicTrackingController(ShipmentService shipmentService) {
        this.shipmentService = shipmentService;
    }

    @GetMapping("/{trackingCode}")
    @Operation(summary = "Status and timeline by tracking code (no personal data)")
    @ApiResponse(responseCode = "404", description = "Unknown tracking code",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public PublicTrackingResponse track(
            @Parameter(example = "ST-2026-000042")
            @PathVariable @Pattern(regexp = "^[A-Za-z]{2}-\\d{4}-\\d{6,}$", message = "must look like ST-2026-000042")
            String trackingCode) {
        return shipmentService.track(trackingCode);
    }
}
