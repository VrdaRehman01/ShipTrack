package com.shiptrack.tracking.controller;

import com.shiptrack.tracking.config.CorrelationIdFilter;
import com.shiptrack.tracking.dto.LocationRequest;
import com.shiptrack.tracking.dto.LocationResponse;
import com.shiptrack.tracking.exception.ApiError;
import com.shiptrack.tracking.service.TrackingService;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.rules.SecurityRule;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.List;

/** REST only; rules live in TrackingService. Blocking JDBC runs off the event loop. */
@Controller("/api/v1/tracking")
@ExecuteOn(TaskExecutors.BLOCKING)
@Secured(SecurityRule.IS_AUTHENTICATED)
@Tag(name = "Tracking", description = "Live driver locations for a shipment")
@ApiResponse(responseCode = "401", description = "Missing or expired JWT",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
public class TrackingController {

    private final TrackingService trackingService;

    public TrackingController(TrackingService trackingService) {
        this.trackingService = trackingService;
    }

    @Post("/{shipmentId}/locations")
    @Secured("DRIVER")
    @Operation(summary = "Post the driver's current location (assigned driver, while picked up or in transit)")
    @ApiResponse(responseCode = "201", description = "Location stored")
    @ApiResponse(responseCode = "403", description = "Not the assigned driver",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "SHIPMENT_NOT_IN_PROGRESS",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public HttpResponse<LocationResponse> post(@PathVariable Long shipmentId, @Valid @Body LocationRequest body,
                                               Authentication authentication, HttpRequest<?> request) {
        LocationResponse saved = trackingService.record(Long.valueOf(authentication.getName()), shipmentId, body,
                authorization(request), CorrelationIdFilter.of(request));
        return HttpResponse.created(saved, URI.create("/api/v1/tracking/" + shipmentId + "/latest"));
    }

    @Get("/{shipmentId}/latest")
    @Operation(summary = "Latest known location (owner, assigned driver or admin)")
    @ApiResponse(responseCode = "404", description = "NO_LOCATION_YET or unknown shipment",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public LocationResponse latest(@PathVariable Long shipmentId, HttpRequest<?> request) {
        return trackingService.latest(shipmentId, authorization(request), CorrelationIdFilter.of(request));
    }

    @Get("/{shipmentId}/locations")
    @Operation(summary = "Recent route, newest first (owner, assigned driver or admin)")
    public List<LocationResponse> route(@PathVariable Long shipmentId,
                                        @QueryValue(defaultValue = "50") @Min(1) @Max(500) int limit,
                                        HttpRequest<?> request) {
        return trackingService.route(shipmentId, limit, authorization(request), CorrelationIdFilter.of(request));
    }

    private static String authorization(HttpRequest<?> request) {
        return request.getHeaders().getAuthorization().orElse(null);
    }
}
