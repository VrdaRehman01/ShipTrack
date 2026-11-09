package com.shiptrack.shipment.repository;

import com.shiptrack.shipment.entity.Shipment;
import com.shiptrack.shipment.entity.ShipmentStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShipmentRepository extends JpaRepository<Shipment, Long> {

    Page<Shipment> findByCustomerId(Long customerId, Pageable pageable);

    Page<Shipment> findByCustomerIdAndStatus(Long customerId, ShipmentStatus status, Pageable pageable);

    Page<Shipment> findByDriverId(Long driverId, Pageable pageable);

    Page<Shipment> findByDriverIdAndStatus(Long driverId, ShipmentStatus status, Pageable pageable);

    Page<Shipment> findByStatus(ShipmentStatus status, Pageable pageable);

    Page<Shipment> findByStatusAndDriverIdIsNull(ShipmentStatus status, Pageable pageable);

    Optional<Shipment> findByCustomerIdAndIdempotencyKey(Long customerId, String idempotencyKey);

    Optional<Shipment> findByTrackingCode(String trackingCode);

    /** Delivered shipments whose invoice hasn't been confirmed yet (billing was down). */
    @Query("select s.id from Shipment s where s.status = com.shiptrack.shipment.entity.ShipmentStatus.DELIVERED "
            + "and s.invoiced = false order by s.deliveredAt")
    List<Long> findUninvoicedDeliveredIds(Pageable pageable);

    /** Bulk update on purpose: it doesn't bump @Version, so it can't clash with a user's status change. */
    @Modifying
    @Query("update Shipment s set s.invoiced = true where s.id = :id")
    int markInvoiced(@Param("id") Long id);
}
