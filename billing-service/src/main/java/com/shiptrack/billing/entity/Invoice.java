package com.shiptrack.billing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "invoices")
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "invoice_number", length = 20, unique = true)
    private String invoiceNumber;

    @Column(name = "shipment_id", nullable = false, unique = true, updatable = false)
    private Long shipmentId;

    @Column(name = "tracking_code", nullable = false, length = 20, updatable = false)
    private String trackingCode;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private Long customerId;

    @Column(name = "weight_kg", nullable = false, precision = 8, scale = 3, updatable = false)
    private BigDecimal weightKg;

    @Column(name = "base_fee", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal baseFee;

    @Column(name = "weight_charge", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal weightCharge;

    @Column(nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(nullable = false, length = 3, updatable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String currency;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private InvoiceStatus status;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected Invoice() {
        // for JPA
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    public Invoice(Long shipmentId, String trackingCode, Long customerId, BigDecimal weightKg,
                   Price price, Instant issuedAt) {
        this.shipmentId = shipmentId;
        this.trackingCode = trackingCode;
        this.customerId = customerId;
        this.weightKg = weightKg;
        this.baseFee = price.baseFee();
        this.weightCharge = price.weightCharge();
        this.amount = price.total();
        this.currency = price.currency();
        this.status = InvoiceStatus.ISSUED;
        this.issuedAt = issuedAt.truncatedTo(ChronoUnit.SECONDS);
    }

    /** The priced parts of an invoice. */
    public record Price(BigDecimal baseFee, BigDecimal weightCharge, BigDecimal total, String currency) {
    }

    public void assignInvoiceNumber(String number) {
        this.invoiceNumber = number;
    }

    public void markPaid(Instant at) {
        this.status = InvoiceStatus.PAID;
        this.paidAt = at.truncatedTo(ChronoUnit.SECONDS);
    }

    public Long getId() {
        return id;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public Long getShipmentId() {
        return shipmentId;
    }

    public String getTrackingCode() {
        return trackingCode;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public BigDecimal getWeightKg() {
        return weightKg;
    }

    public BigDecimal getBaseFee() {
        return baseFee;
    }

    public BigDecimal getWeightCharge() {
        return weightCharge;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public InvoiceStatus getStatus() {
        return status;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Instant getPaidAt() {
        return paidAt;
    }
}
