package com.shiptrack.shipment.entity;

import com.shiptrack.common.security.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One row per status change: the shipment's timeline and audit trail. Never updated. */
@Entity
@Table(name = "shipment_status_changes")
public class ShipmentStatusChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shipment_id", nullable = false, updatable = false)
    private Long shipmentId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "from_status", length = 20, updatable = false)
    private ShipmentStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "to_status", nullable = false, length = 20, updatable = false)
    private ShipmentStatus toStatus;

    @Column(name = "changed_by", nullable = false, updatable = false)
    private Long changedBy;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "changed_by_role", nullable = false, length = 20, updatable = false)
    private Role changedByRole;

    @Column(length = 255, updatable = false)
    private String note;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    protected ShipmentStatusChange() {
        // for JPA
    }

    public ShipmentStatusChange(Long shipmentId, ShipmentStatus fromStatus, ShipmentStatus toStatus,
                                Long changedBy, Role changedByRole, String note, Instant changedAt) {
        this.shipmentId = shipmentId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.changedBy = changedBy;
        this.changedByRole = changedByRole;
        this.note = note;
        this.changedAt = changedAt.truncatedTo(ChronoUnit.SECONDS);
    }

    public ShipmentStatus getFromStatus() {
        return fromStatus;
    }

    public ShipmentStatus getToStatus() {
        return toStatus;
    }

    public Long getChangedBy() {
        return changedBy;
    }

    public Role getChangedByRole() {
        return changedByRole;
    }

    public String getNote() {
        return note;
    }

    public Instant getChangedAt() {
        return changedAt;
    }
}
