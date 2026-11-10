package com.shiptrack.billing.controller;

import com.shiptrack.billing.dto.DriverEarningsResponse;
import com.shiptrack.billing.dto.InvoiceResponse;
import com.shiptrack.billing.entity.InvoiceStatus;
import com.shiptrack.billing.service.InvoiceService;
import com.shiptrack.common.error.ApiError;
import com.shiptrack.common.security.AuthenticatedUser;
import com.shiptrack.common.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/invoices")
@Tag(name = "Invoices", description = "Invoices are created automatically when a shipment is delivered")
@ApiResponse(responseCode = "401", description = "Missing or expired JWT",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
public class InvoiceController {

    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @GetMapping(params = "shipmentId")
    @Operation(summary = "Get the invoice for a shipment (owner or admin)")
    @ApiResponse(responseCode = "403", description = "Not your invoice",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Not delivered yet, so no invoice",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public InvoiceResponse getByShipment(@AuthenticationPrincipal AuthenticatedUser caller,
                                         @Parameter(example = "42") @RequestParam Long shipmentId) {
        return invoiceService.getByShipment(caller, shipmentId);
    }

    @GetMapping(params = "!shipmentId")
    @Operation(summary = "List invoices, newest first (customers: own, admins: all)")
    public PageResponse<InvoiceResponse> list(
            @AuthenticationPrincipal AuthenticatedUser caller,
            @Parameter(example = "ISSUED") @RequestParam(required = false) InvoiceStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(invoiceService.list(caller, status, page, size), i -> i);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an invoice by id (owner or admin)")
    public InvoiceResponse get(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable Long id) {
        return invoiceService.get(caller, id);
    }

    @GetMapping("/driver-earnings")
    @PreAuthorize("hasRole('DRIVER')")
    @Operation(summary = "Get the logged-in driver's accrued delivery earnings")
    public DriverEarningsResponse driverEarnings(@AuthenticationPrincipal AuthenticatedUser caller) {
        return invoiceService.driverEarnings(caller);
    }

    @PostMapping("/{id}/payments")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Record that an invoice was paid (admin)")
    @ApiResponse(responseCode = "409", description = "INVOICE_ALREADY_PAID",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public InvoiceResponse pay(@PathVariable Long id) {
        return invoiceService.markPaid(id);
    }
}
