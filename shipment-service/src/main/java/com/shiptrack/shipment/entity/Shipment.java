package com.shiptrack.shipment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "shipments")
public class Shipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tracking_code", length = 20, unique = true)
    private String trackingCode;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private Long customerId;

    @Column(name = "driver_id")
    private Long driverId;

    @Column(name = "driver_name", length = 100)
    private String driverName;

    @Column(name = "driver_phone", length = 20)
    private String driverPhone;

    @Column(name = "expected_delivery_date")
    private LocalDate expectedDeliveryDate;

    @Column(name = "sender_name", nullable = false, length = 100)
    private String senderName;

    @Column(name = "sender_phone", nullable = false, length = 20)
    private String senderPhone;

    @Column(name = "pickup_address", nullable = false)
    private String pickupAddress;

    @Column(name = "receiver_name", nullable = false, length = 100)
    private String receiverName;

    @Column(name = "receiver_phone", nullable = false, length = 20)
    private String receiverPhone;

    @Column(name = "delivery_address", nullable = false)
    private String deliveryAddress;

    @Column(name = "weight_kg", nullable = false, precision = 8, scale = 3)
    private BigDecimal weightKg;

    @Column(length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private ShipmentStatus status;

    @Column(name = "idempotency_key", length = 64, updatable = false)
    private String idempotencyKey;

    @Column(name = "idempotency_fingerprint", length = 64, updatable = false)
    private String idempotencyFingerprint;

    @Column(nullable = false)
    private boolean invoiced;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "delivery_code_hash", length = 60)
    private String deliveryCodeHash;

    @Column(name = "driver_payout_percent", precision = 5, scale = 2)
    private BigDecimal driverPayoutPercent;

    /** Optimistic locking: two simultaneous status updates can't both win. */
    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Shipment() {
        // for JPA
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    public Shipment(Long customerId, String senderName, String senderPhone, String pickupAddress,
                    String receiverName, String receiverPhone, String deliveryAddress,
                    BigDecimal weightKg, String description) {
        this.customerId = customerId;
        this.senderName = senderName;
        this.senderPhone = senderPhone;
        this.pickupAddress = pickupAddress;
        this.receiverName = receiverName;
        this.receiverPhone = receiverPhone;
        this.deliveryAddress = deliveryAddress;
        // Same scale as the DB column, so the response is "2.500" both right after create and when read back.
        this.weightKg = weightKg.setScale(3, RoundingMode.HALF_UP);
        this.description = description;
        this.status = ShipmentStatus.CREATED;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    }

    // --- behaviour ---

    public void assignTrackingCode(String code) {
        this.trackingCode = code;
    }

    public void useIdempotencyKey(String key, String fingerprint) {
        this.idempotencyKey = key;
        this.idempotencyFingerprint = fingerprint;
    }

    public void setDeliveryCodeHash(String hash) {
        this.deliveryCodeHash = hash;
    }

    /** A null date keeps any date already promised. */
    public void assignDriver(Long newDriverId, String newDriverName, String newDriverPhone, LocalDate expectedDelivery) {
        this.driverId = newDriverId;
        this.driverName = newDriverName;
        this.driverPhone = newDriverPhone;
        if (expectedDelivery != null) {
            this.expectedDeliveryDate = expectedDelivery;
        }
    }

    /** Back to waiting for a driver. The date was that driver's promise, so it goes too. */
    public void releaseDriver() {
        this.driverId = null;
        this.driverName = null;
        this.driverPhone = null;
        this.expectedDeliveryDate = null;
    }

    /** Callers check the transition is allowed first (ShipmentService owns the rules). */
    public void moveTo(ShipmentStatus next, Instant at) {
        this.status = next;
        if (next == ShipmentStatus.DELIVERED) {
            this.deliveredAt = at.truncatedTo(ChronoUnit.SECONDS);
        }
    }

    public boolean isOwnedBy(Long userId) {
        return customerId.equals(userId);
    }

    public boolean isAssignedTo(Long userId) {
        return driverId != null && driverId.equals(userId);
    }

    // --- getters ---

    public Long getId() {
        return id;
    }

    public String getTrackingCode() {
        return trackingCode;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public Long getDriverId() {
        return driverId;
    }

    public String getDriverName() {
        return driverName;
    }

    public String getDriverPhone() {
        return driverPhone;
    }

    public LocalDate getExpectedDeliveryDate() {
        return expectedDeliveryDate;
    }

    public String getSenderName() {
        return senderName;
    }

    public String getSenderPhone() {
        return senderPhone;
    }

    public String getPickupAddress() {
        return pickupAddress;
    }

    public String getReceiverName() {
        return receiverName;
    }

    public String getReceiverPhone() {
        return receiverPhone;
    }

    public String getDeliveryAddress() {
        return deliveryAddress;
    }

    public BigDecimal getWeightKg() {
        return weightKg;
    }

    public String getDescription() {
        return description;
    }

    public ShipmentStatus getStatus() {
        return status;
    }

    public String getIdempotencyFingerprint() {
        return idempotencyFingerprint;
    }

    public boolean isInvoiced() {
        return invoiced;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }

    public String getDeliveryCodeHash() {
        return deliveryCodeHash;
    }

    public void setDriverPayoutPercent(BigDecimal percentage) {
        this.driverPayoutPercent = percentage;
    }

    public BigDecimal getDriverPayoutPercent() {
        return driverPayoutPercent;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
