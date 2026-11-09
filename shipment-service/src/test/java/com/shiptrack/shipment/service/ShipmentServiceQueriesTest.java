package com.shiptrack.shipment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.shiptrack.common.error.ApiException;
import com.shiptrack.common.security.AuthenticatedUser;
import com.shiptrack.common.security.Role;
import com.shiptrack.shipment.client.AuthClient;
import com.shiptrack.shipment.dto.CreateShipmentRequest;
import com.shiptrack.shipment.entity.Shipment;
import com.shiptrack.shipment.entity.ShipmentStatus;
import com.shiptrack.shipment.exception.ShipmentErrorCode;
import com.shiptrack.shipment.repository.ShipmentRepository;
import com.shiptrack.shipment.repository.ShipmentStatusChangeRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/** Who gets which list, and what happens when two identical create requests race. */
class ShipmentServiceQueriesTest {

    private static final AuthenticatedUser OWNER = new AuthenticatedUser(1L, "owner@example.com", Role.CUSTOMER);
    private static final AuthenticatedUser DRIVER = new AuthenticatedUser(10L, "driver@example.com", Role.DRIVER);
    private static final AuthenticatedUser ADMIN = new AuthenticatedUser(99L, "admin@example.com", Role.ADMIN);
    private static final CreateShipmentRequest REQUEST = new CreateShipmentRequest("Sara", "+92 300 1234567", "A",
            "Ali", "+92 321 7654321", "B", new BigDecimal("2.5"), null);
    private static final Shipment A_SHIPMENT = new Shipment(1L, "Sara", "+92 300 1234567", "A",
            "Ali", "+92 321 7654321", "B", new BigDecimal("2.5"), null);
    private static final Page<Shipment> ONE_PAGE = new PageImpl<>(List.of(A_SHIPMENT));

    private final ShipmentRepository shipments = mock(ShipmentRepository.class);
    private final TransactionTemplate tx = mock(TransactionTemplate.class);
    private final AuthClient authClient = mock(AuthClient.class);
    private final ShipmentService service = new ShipmentService(shipments, mock(ShipmentStatusChangeRepository.class),
            authClient, mock(ApplicationEventPublisher.class), tx, Clock.systemUTC());

    private void acceptingIsPossible(Shipment parcel) {
        when(authClient.currentUser()).thenReturn(new AuthClient.UserView(10L, "d@example.com", "Bilal", Role.DRIVER, "+92 311 7654321"));
        when(shipments.findById(42L)).thenReturn(Optional.of(parcel));
        when(tx.execute(any())).thenAnswer(inv -> inv.<TransactionCallback<?>>getArgument(0).doInTransaction(null));
    }

    private static Shipment openParcel() {
        return new Shipment(1L, "Sara", "+92 300 1234567", "A", "Ali", "+92 321 7654321", "B", new BigDecimal("2.5"), null);
    }

    @Test
    void openJobsAreOldestFirstAndHideNames() {
        when(shipments.findByStatusAndDriverIdIsNull(eq(ShipmentStatus.CREATED), any())).thenReturn(ONE_PAGE);

        var jobs = service.availableJobs(0, 20);

        assertThat(jobs.getContent().getFirst().pickupAddress()).isEqualTo("A");
        assertThat(jobs.getContent().getFirst().toString()).doesNotContain("Sara", "1234567");
    }

    @Test
    void aDriverAcceptsAnOpenParcel() {
        acceptingIsPossible(openParcel());

        var taken = service.acceptJob(DRIVER, 42L, null);

        assertThat(taken.driverId()).isEqualTo(10L);
        assertThat(taken.driverName()).isEqualTo("Bilal");
        assertThat(taken.driverPhone()).isEqualTo("+92 311 7654321");
    }

    @Test
    void aParcelWithADriverCannotBeAccepted() {
        Shipment parcel = openParcel();
        parcel.assignDriver(11L, "Someone", null, null);
        acceptingIsPossible(parcel);

        assertThatThrownBy(() -> service.acceptJob(DRIVER, 42L, null)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getCode()).isEqualTo(ShipmentErrorCode.JOB_TAKEN));
    }

    @Test
    void aCancelledParcelCannotBeAccepted() {
        Shipment parcel = openParcel();
        parcel.moveTo(ShipmentStatus.CANCELLED, Instant.now());
        acceptingIsPossible(parcel);

        assertThatThrownBy(() -> service.acceptJob(DRIVER, 42L, null)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getCode()).isEqualTo(ShipmentErrorCode.JOB_TAKEN));
    }

    @Test
    void customerListsOnlyTheirOwn() {
        when(shipments.findByCustomerId(eq(1L), any())).thenReturn(ONE_PAGE);
        when(shipments.findByCustomerIdAndStatus(eq(1L), eq(ShipmentStatus.CREATED), any())).thenReturn(ONE_PAGE);

        assertThat(service.list(OWNER, null, 0, 20)).hasSize(1);
        assertThat(service.list(OWNER, ShipmentStatus.CREATED, 0, 20)).hasSize(1);
    }

    @Test
    void driverListsOnlyTheirAssignments() {
        when(shipments.findByDriverId(eq(10L), any())).thenReturn(ONE_PAGE);
        when(shipments.findByDriverIdAndStatus(eq(10L), eq(ShipmentStatus.IN_TRANSIT), any())).thenReturn(ONE_PAGE);

        assertThat(service.list(DRIVER, null, 0, 20)).hasSize(1);
        assertThat(service.list(DRIVER, ShipmentStatus.IN_TRANSIT, 0, 20)).hasSize(1);
    }

    @Test
    void adminListsEverything() {
        when(shipments.findAll(any(org.springframework.data.domain.Pageable.class))).thenReturn(ONE_PAGE);
        when(shipments.findByStatus(eq(ShipmentStatus.DELIVERED), any())).thenReturn(ONE_PAGE);

        assertThat(service.list(ADMIN, null, 0, 20)).hasSize(1);
        assertThat(service.list(ADMIN, ShipmentStatus.DELIVERED, 0, 20)).hasSize(1);
    }

    @Test
    void ownerCanReadTheirShipment() {
        when(shipments.findById(42L)).thenReturn(Optional.of(A_SHIPMENT));

        assertThat(service.get(OWNER, 42L).senderName()).isEqualTo("Sara");
    }

    @Test
    @SuppressWarnings("unchecked") // Mockito's varargs thenReturn with a generic type
    void losingAnIdempotentCreateRaceReturnsTheWinner() {
        Shipment winner = new Shipment(1L, "Sara", "+92 300 1234567", "A", "Ali", "+92 321 7654321", "B", new BigDecimal("2.5"), null);
        winner.useIdempotencyKey("key-1", ShipmentService.fingerprint(REQUEST));
        when(shipments.findByCustomerIdAndIdempotencyKey(1L, "key-1")).thenReturn(Optional.empty(), Optional.of(winner));
        when(tx.execute(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThat(service.create(OWNER, REQUEST, "key-1").replayed()).isTrue();
    }

    @Test
    void aDatabaseErrorWithoutAKeyIsNotHidden() {
        when(tx.execute(any())).thenThrow(new DataIntegrityViolationException("too long"));

        assertThatThrownBy(() -> service.create(OWNER, REQUEST, null)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aDatabaseErrorThatIsNotARaceIsNotHidden() {
        when(shipments.findByCustomerIdAndIdempotencyKey(1L, "key-1")).thenReturn(Optional.empty());
        when(tx.execute(any())).thenThrow(new DataIntegrityViolationException("too long"));

        assertThatThrownBy(() -> service.create(OWNER, REQUEST, "key-1")).isInstanceOf(DataIntegrityViolationException.class);
    }
}
