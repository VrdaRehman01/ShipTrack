package com.shiptrack.billing.service;

import com.shiptrack.billing.dto.CreateInvoiceRequest;
import com.shiptrack.billing.dto.DriverEarningsResponse;
import com.shiptrack.billing.dto.InvoiceResponse;
import com.shiptrack.billing.entity.Invoice;
import com.shiptrack.billing.entity.InvoiceStatus;
import com.shiptrack.billing.exception.BillingErrorCode;
import com.shiptrack.billing.repository.InvoiceRepository;
import com.shiptrack.common.error.ApiException;
import com.shiptrack.common.error.CommonErrorCode;
import com.shiptrack.common.security.AuthenticatedUser;
import com.shiptrack.common.security.Role;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class InvoiceService {

    /** The invoice, and whether this call created it (false = it already existed). */
    public record CreateResult(InvoiceResponse invoice, boolean created) {
    }

    private final InvoiceRepository invoices;
    private final PricingService pricing;
    private final TransactionTemplate tx;
    private final Clock clock;

    public InvoiceService(InvoiceRepository invoices, PricingService pricing, TransactionTemplate tx, Clock clock) {
        this.invoices = invoices;
        this.pricing = pricing;
        this.tx = tx;
        this.clock = clock;
    }

    /**
     * FR-7: called by shipment-service on delivery. Idempotent on shipmentId, so shipment-service can retry
     * freely (after a timeout, or from its retry job) without ever double-billing.
     */
    public CreateResult createForShipment(CreateInvoiceRequest request) {
        var existing = invoices.findByShipmentId(request.shipmentId());
        if (existing.isPresent()) {
            return new CreateResult(checkSameShipment(existing.get(), request), false);
        }
        try {
            Invoice created = tx.execute(status -> {
                Invoice invoice = new Invoice(request.shipmentId(), request.trackingCode(), request.customerId(),
                        request.weightKg(), pricing.price(request.weightKg()), clock.instant(),
                        request.driverId(), request.driverPayoutPercent());
                invoices.saveAndFlush(invoice);
                invoice.assignInvoiceNumber(invoiceNumber(invoice.getId(), invoice.getIssuedAt()));
                return invoice;
            });
            return new CreateResult(InvoiceResponse.from(Objects.requireNonNull(created)), true);
        } catch (DataIntegrityViolationException e) {
            // A retry raced the first call; the unique shipment_id index let exactly one through.
            Invoice winner = invoices.findByShipmentId(request.shipmentId()).orElseThrow(() -> e);
            return new CreateResult(checkSameShipment(winner, request), false);
        }
    }

    /** Owner or admin (FR-6 applies to invoices too). */
    @Transactional(readOnly = true)
    public InvoiceResponse getByShipment(AuthenticatedUser caller, Long shipmentId) {
        Invoice invoice = invoices.findByShipmentId(shipmentId)
                .orElseThrow(() -> new ApiException(CommonErrorCode.NOT_FOUND,
                        "No invoice yet for shipment " + shipmentId + ". Invoices are created when a shipment is delivered."));
        return InvoiceResponse.from(checkVisible(caller, invoice));
    }

    @Transactional(readOnly = true)
    public InvoiceResponse get(AuthenticatedUser caller, Long id) {
        Invoice invoice = invoices.findById(id).orElseThrow(() -> ApiException.notFound("Invoice", id));
        return InvoiceResponse.from(checkVisible(caller, invoice));
    }

    /** Drivers see only their accrued share totals; old invoices without an earnings snapshot are excluded. */
    @Transactional(readOnly = true)
    public DriverEarningsResponse driverEarnings(AuthenticatedUser caller) {
        if (caller.role() != Role.DRIVER) {
            throw ApiException.forbidden("Driver earnings are only available to drivers");
        }
        var totals = invoices.findDriverEarnings(caller.id());
        BigDecimal gross = totals.getGrossFare().setScale(2, RoundingMode.HALF_UP);
        BigDecimal earned = totals.getEarnedAmount().setScale(2, RoundingMode.HALF_UP);
        BigDecimal effectivePercent = gross.signum() == 0 ? BigDecimal.ZERO
                : earned.multiply(new BigDecimal("100")).divide(gross, 2, RoundingMode.HALF_UP);
        return new DriverEarningsResponse(totals.getCompletedDeliveries(), totals.getCurrency(), gross.toPlainString(),
                earned.toPlainString(), effectivePercent.toPlainString());
    }

    /** Customers see their own invoices, admins see all. */
    @Transactional(readOnly = true)
    public Page<InvoiceResponse> list(AuthenticatedUser caller, InvoiceStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "issuedAt", "id"));
        Page<Invoice> result;
        if (caller.isAdmin()) {
            result = status == null ? invoices.findAll(pageable) : invoices.findByStatus(status, pageable);
        } else if (caller.isCustomer()) {
            result = status == null
                    ? invoices.findByCustomerId(caller.id(), pageable)
                    : invoices.findByCustomerIdAndStatus(caller.id(), status, pageable);
        } else {
            throw ApiException.forbidden("Drivers don't have invoices");
        }
        return result.map(InvoiceResponse::from);
    }

    /** Admin records a payment. */
    @Transactional
    public InvoiceResponse markPaid(Long id) {
        Invoice invoice = invoices.findById(id).orElseThrow(() -> ApiException.notFound("Invoice", id));
        if (invoice.getStatus() == InvoiceStatus.PAID) {
            throw new ApiException(BillingErrorCode.INVOICE_ALREADY_PAID, "Invoice " + invoice.getInvoiceNumber() + " is already paid");
        }
        invoice.markPaid(clock.instant());
        invoices.saveAndFlush(invoice);
        return InvoiceResponse.from(invoice);
    }

    static String invoiceNumber(Long id, Instant issuedAt) {
        return "INV-%d-%06d".formatted(issuedAt.atZone(ZoneOffset.UTC).getYear(), id);
    }

    private static Invoice checkVisible(AuthenticatedUser caller, Invoice invoice) {
        if (caller.isAdmin() || (caller.isCustomer() && invoice.getCustomerId().equals(caller.id()))) {
            return invoice;
        }
        throw ApiException.forbidden("You can only access your own invoices");
    }

    private static InvoiceResponse checkSameShipment(Invoice existing, CreateInvoiceRequest request) {
        if (!existing.getCustomerId().equals(request.customerId())) {
            throw new ApiException(BillingErrorCode.INVOICE_MISMATCH,
                    "Shipment " + request.shipmentId() + " was already invoiced for a different customer");
        }
        return InvoiceResponse.from(existing);
    }
}
