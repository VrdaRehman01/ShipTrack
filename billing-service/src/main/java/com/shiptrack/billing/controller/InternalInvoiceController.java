package com.shiptrack.billing.controller;

import com.shiptrack.billing.dto.CreateInvoiceRequest;
import com.shiptrack.billing.dto.InvoiceResponse;
import com.shiptrack.billing.service.InvoiceService;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service API, protected by X-Internal-Api-Key and never routed by the gateway.
 * Hidden from public Swagger.
 */
@Hidden
@RestController
@RequestMapping("/internal/invoices")
public class InternalInvoiceController {

    private final InvoiceService invoiceService;

    public InternalInvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    /** 201 when created, 200 when this shipment already had an invoice (safe retry). */
    @PostMapping
    public ResponseEntity<InvoiceResponse> create(@Valid @RequestBody CreateInvoiceRequest request) {
        InvoiceService.CreateResult result = invoiceService.createForShipment(request);
        if (result.created()) {
            return ResponseEntity.created(URI.create("/api/v1/invoices/" + result.invoice().id())).body(result.invoice());
        }
        return ResponseEntity.ok(result.invoice());
    }
}
