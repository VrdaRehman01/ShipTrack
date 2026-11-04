package com.shiptrack.shipment.service;

import com.shiptrack.shipment.client.BillingClient;
import com.shiptrack.shipment.entity.Shipment;
import com.shiptrack.shipment.entity.ShipmentStatus;
import com.shiptrack.shipment.repository.ShipmentRepository;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * FR-7 + the reliability NFR: asks billing-service for an invoice when a shipment is delivered.
 * <ul>
 *   <li>Runs after the delivery is committed, on a background thread, so the driver's request never waits on billing.</li>
 *   <li>Calls go through a circuit breaker: if billing is down we fail fast instead of piling up slow calls.</li>
 *   <li>A shipment stays {@code invoiced = false} until billing confirms; a scheduled job retries those, so no
 *       invoice is ever lost. Billing is idempotent on shipmentId, so a retry can't double-bill.</li>
 * </ul>
 */
@Service
public class InvoiceRequester {

    private static final Logger log = LoggerFactory.getLogger(InvoiceRequester.class);
    private static final int RETRY_BATCH_SIZE = 50;

    private final ShipmentRepository shipments;
    private final BillingClient billingClient;
    private final CircuitBreaker circuitBreaker;
    private final TransactionTemplate tx;
    private final int immediateAttempts;
    private final Duration immediateBackoff;

    public InvoiceRequester(ShipmentRepository shipments, BillingClient billingClient,
                            CircuitBreaker billingCircuitBreaker, TransactionTemplate tx,
                            @Value("${shiptrack.billing.immediate-attempts:3}") int immediateAttempts,
                            @Value("${shiptrack.billing.immediate-backoff:1s}") Duration immediateBackoff) {
        this.shipments = shipments;
        this.billingClient = billingClient;
        this.circuitBreaker = billingCircuitBreaker;
        this.tx = tx;
        this.immediateAttempts = Math.max(1, immediateAttempts);
        this.immediateBackoff = immediateBackoff;
    }

    /**
     * Right after delivery: a few quick attempts with growing backoff, so a blip (cold start, slow first call)
     * still gives the customer an invoice within seconds. Anything longer is left to the retry job.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDelivered(ShipmentDeliveredEvent event) {
        for (int attempt = 1; attempt <= immediateAttempts; attempt++) {
            if (requestInvoice(event.shipmentId())
                    || circuitBreaker.getState() == CircuitBreaker.State.OPEN
                    || attempt == immediateAttempts) {
                return;
            }
            try {
                Thread.sleep(immediateBackoff.multipliedBy(attempt));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    @Scheduled(fixedDelayString = "${shiptrack.billing.retry-interval:60s}",
            initialDelayString = "${shiptrack.billing.retry-interval:60s}")
    public void retryPendingInvoices() {
        List<Long> pending = shipments.findUninvoicedDeliveredIds(PageRequest.of(0, RETRY_BATCH_SIZE));
        if (pending.isEmpty()) {
            return;
        }
        log.info("Retrying invoices for {} delivered shipment(s)", pending.size());
        for (Long id : pending) {
            if (!requestInvoice(id)) {
                return; // billing still unhappy; try the rest next round
            }
        }
    }

    /**
     * @return true when billing has the invoice (or none is needed), false if we should try again later
     */
    public boolean requestInvoice(Long shipmentId) {
        Shipment shipment = shipments.findById(shipmentId).orElse(null);
        if (shipment == null || shipment.isInvoiced() || shipment.getStatus() != ShipmentStatus.DELIVERED) {
            return true;
        }
        var request = new BillingClient.CreateInvoiceRequest(
                shipment.getId(), shipment.getTrackingCode(), shipment.getCustomerId(), shipment.getWeightKg());
        try {
            circuitBreaker.executeRunnable(() -> billingClient.createInvoice(request));
        } catch (CallNotPermittedException e) {
            log.info("Billing circuit is open; invoice for shipment {} will be retried", shipmentId);
            return false;
        } catch (RuntimeException e) {
            log.warn("Could not create invoice for shipment {} (will retry): {}", shipmentId, e.getMessage());
            return false;
        }
        tx.executeWithoutResult(status -> shipments.markInvoiced(shipmentId));
        log.info("Invoice requested for shipment {}", shipmentId);
        return true;
    }
}
