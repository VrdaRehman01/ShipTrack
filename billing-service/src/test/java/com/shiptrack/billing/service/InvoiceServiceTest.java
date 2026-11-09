package com.shiptrack.billing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.shiptrack.billing.dto.CreateInvoiceRequest;
import com.shiptrack.billing.entity.Invoice;
import com.shiptrack.billing.entity.InvoiceStatus;
import com.shiptrack.billing.exception.BillingErrorCode;
import com.shiptrack.billing.repository.InvoiceRepository;
import com.shiptrack.common.error.ApiException;
import com.shiptrack.common.error.CommonErrorCode;
import com.shiptrack.common.security.AuthenticatedUser;
import com.shiptrack.common.security.Role;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.support.TransactionTemplate;

class InvoiceServiceTest {

    private static final BigDecimal WEIGHT = new BigDecimal("2.500");
    private static final CreateInvoiceRequest REQUEST = new CreateInvoiceRequest(1L, "ST-2026-000001", 7L, WEIGHT);
    private static final AuthenticatedUser ADMIN = new AuthenticatedUser(99L, "admin@example.com", Role.ADMIN);
    private static final AuthenticatedUser DRIVER = new AuthenticatedUser(10L, "driver@example.com", Role.DRIVER);

    private final InvoiceRepository invoices = mock(InvoiceRepository.class);
    private final TransactionTemplate tx = mock(TransactionTemplate.class);
    private final InvoiceService service = new InvoiceService(invoices, mock(PricingService.class), tx, Clock.systemUTC());

    private static Invoice invoiceFor(Long customerId) {
        var price = new Invoice.Price(new BigDecimal("250.00"), new BigDecimal("300.00"), new BigDecimal("550.00"), "PKR");
        return new Invoice(1L, "ST-2026-000001", customerId, WEIGHT, price, Instant.parse("2026-11-04T09:30:00Z"));
    }

    @Test
    @SuppressWarnings("unchecked") // Mockito's varargs thenReturn with a generic type
    void aRetryThatLosesTheRaceReturnsTheExistingInvoice() {
        when(invoices.findByShipmentId(1L)).thenReturn(Optional.empty(), Optional.of(invoiceFor(7L)));
        when(tx.execute(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThat(service.createForShipment(REQUEST).created()).isFalse();
    }

    @Test
    void aDatabaseErrorThatIsNotARaceIsNotHidden() {
        when(invoices.findByShipmentId(1L)).thenReturn(Optional.empty());
        when(tx.execute(any())).thenThrow(new DataIntegrityViolationException("broken"));

        assertThatThrownBy(() -> service.createForShipment(REQUEST)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void refusesToReuseAnInvoiceForADifferentCustomer() {
        when(invoices.findByShipmentId(1L)).thenReturn(Optional.of(invoiceFor(8L)));

        assertThatThrownBy(() -> service.createForShipment(REQUEST)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getCode()).isEqualTo(BillingErrorCode.INVOICE_MISMATCH));
    }

    @Test
    void adminListsAllOrByStatus() {
        when(invoices.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(invoiceFor(7L))));
        when(invoices.findByStatus(eq(InvoiceStatus.PAID), any())).thenReturn(new PageImpl<>(List.of()));

        assertThat(service.list(ADMIN, null, 0, 20)).hasSize(1);
        assertThat(service.list(ADMIN, InvoiceStatus.PAID, 0, 20)).isEmpty();
    }

    @Test
    void driversHaveNoInvoices() {
        assertThatThrownBy(() -> service.list(DRIVER, null, 0, 20)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getCode()).isEqualTo(CommonErrorCode.FORBIDDEN));
    }

    @Test
    void anUnknownInvoiceIs404() {
        when(invoices.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markPaid(5L)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getCode()).isEqualTo(CommonErrorCode.NOT_FOUND));
    }
}
