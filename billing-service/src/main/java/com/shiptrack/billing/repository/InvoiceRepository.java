package com.shiptrack.billing.repository;

import com.shiptrack.billing.entity.Invoice;
import com.shiptrack.billing.entity.InvoiceStatus;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    interface DriverEarningsTotals {
        long getCompletedDeliveries();
        BigDecimal getGrossFare();
        BigDecimal getEarnedAmount();
        String getCurrency();
    }

    Optional<Invoice> findByShipmentId(Long shipmentId);

    Page<Invoice> findByCustomerId(Long customerId, Pageable pageable);

    Page<Invoice> findByCustomerIdAndStatus(Long customerId, InvoiceStatus status, Pageable pageable);

    Page<Invoice> findByStatus(InvoiceStatus status, Pageable pageable);

    @Query(value = "SELECT COUNT(*) AS completedDeliveries, COALESCE(SUM(amount), 0.00) AS grossFare, "
            + "COALESCE(SUM(driver_payout_amount), 0.00) AS earnedAmount, COALESCE(MAX(currency), 'PKR') AS currency "
            + "FROM invoices WHERE driver_id = :driverId AND driver_payout_amount IS NOT NULL", nativeQuery = true)
    DriverEarningsTotals findDriverEarnings(@Param("driverId") Long driverId);
}
