package com.shiptrack.shipment.client;

import com.shiptrack.common.security.InternalApiKeyFilter;
import com.shiptrack.common.security.SecurityProperties;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Calls billing-service's internal API. Plain HTTP; retries and the circuit breaker live in InvoiceRequester. */
@Component
public class BillingClient {

    /** Body for POST /internal/invoices. Billing is idempotent on shipmentId, so re-sending is safe. */
    public record CreateInvoiceRequest(Long shipmentId, String trackingCode, Long customerId, BigDecimal weightKg) {
    }

    private final RestClient restClient;
    private final String internalApiKey;

    public BillingClient(@Qualifier("billingRestClient") RestClient restClient, SecurityProperties securityProperties) {
        this.restClient = restClient;
        this.internalApiKey = securityProperties.internalApiKey();
    }

    public void createInvoice(CreateInvoiceRequest request) {
        restClient.post()
                .uri("/internal/invoices")
                .header(InternalApiKeyFilter.HEADER, internalApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }
}
