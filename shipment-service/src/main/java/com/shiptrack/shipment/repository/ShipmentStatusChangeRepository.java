package com.shiptrack.shipment.repository;

import com.shiptrack.shipment.entity.ShipmentStatusChange;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShipmentStatusChangeRepository extends JpaRepository<ShipmentStatusChange, Long> {

    List<ShipmentStatusChange> findByShipmentIdOrderByIdAsc(Long shipmentId);
}
