package com.shiptrack.shipment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shiptrack.common.error.ApiException;
import com.shiptrack.common.error.CommonErrorCode;
import com.shiptrack.common.security.AuthenticatedUser;
import com.shiptrack.common.security.Role;
import com.shiptrack.shipment.client.AuthClient;
import com.shiptrack.shipment.dto.CreateShipmentRequest;
import com.shiptrack.shipment.dto.UpdateStatusRequest;
import com.shiptrack.shipment.entity.Shipment;
import com.shiptrack.shipment.entity.ShipmentStatus;
import com.shiptrack.shipment.exception.ShipmentErrorCode;
import com.shiptrack.shipment.repository.ShipmentRepository;
import com.shiptrack.shipment.repository.ShipmentStatusChangeRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class ShipmentServiceTest {

    private static final AuthenticatedUser OWNER = new AuthenticatedUser(1L, "owner@x.com", Role.CUSTOMER);
    private static final AuthenticatedUser STRANGER = new AuthenticatedUser(2L, "other@x.com", Role.CUSTOMER);
    private static final AuthenticatedUser DRIVER = new AuthenticatedUser(10L, "driver@x.com", Role.DRIVER);
    private static final AuthenticatedUser OTHER_DRIVER = new AuthenticatedUser(11L, "d2@x.com", Role.DRIVER);
    private static final AuthenticatedUser ADMIN = new AuthenticatedUser(99L, "admin@x.com", Role.ADMIN);

    @Mock
    private ShipmentRepository shipments;
    @Mock
    private ShipmentStatusChangeRepository statusChanges;
    @Mock
    private AuthClient authClient;
    @Mock
    private ApplicationEventPublisher events;
    @Mock
    private TransactionTemplate tx;

    private ShipmentService service;

    @BeforeEach
    void setUp() {
        service = new ShipmentService(shipments, statusChanges, authClient, events, tx,
                Clock.fixed(Instant.parse("2026-11-04T09:30:00Z"), ZoneOffset.UTC));
        // Run "transactions" inline.
        lenient().when(tx.execute(any())).thenAnswer(inv -> inv.<TransactionCallback<?>>getArgument(0).doInTransaction(null));
    }

    // ---------------------------------------------------------------- visibility (FR-6)

    @Test
    void ownerAssignedDriverAndAdminCanView() {
        Shipment s = shipment(ShipmentStatus.CREATED, 10L);
        assertThat(ShipmentService.canView(OWNER, s)).isTrue();
        assertThat(ShipmentService.canView(DRIVER, s)).isTrue();
        assertThat(ShipmentService.canView(ADMIN, s)).isTrue();
        assertThat(ShipmentService.canView(STRANGER, s)).isFalse();
        assertThat(ShipmentService.canView(OTHER_DRIVER, s)).isFalse();
    }

    @Test
    void strangerGets403() {
        stub(shipment(ShipmentStatus.CREATED, null));
        assertThatThrownBy(() -> service.get(STRANGER, 42L)).satisfies(e -> assertCode(e, CommonErrorCode.FORBIDDEN));
    }

    @Test
    void missingShipmentIs404() {
        when(shipments.findById(42L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(ADMIN, 42L)).satisfies(e -> assertCode(e, CommonErrorCode.NOT_FOUND));
    }

    // ---------------------------------------------------------------- lifecycle (FR-5)

    @Test
    void assignedDriverMovesForwardAndHistoryIsRecorded() {
        Shipment s = stub(shipment(ShipmentStatus.CREATED, 10L));

        var response = service.updateStatus(DRIVER, 42L, new UpdateStatusRequest(ShipmentStatus.PICKED_UP, " at door ", null));

        assertThat(response.status()).isEqualTo(ShipmentStatus.PICKED_UP);
        assertThat(s.getStatus()).isEqualTo(ShipmentStatus.PICKED_UP);
        verify(statusChanges).save(any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void skippingAStepIs400WithHelpfulMessage() {
        stub(shipment(ShipmentStatus.CREATED, 10L));

        assertThatThrownBy(() -> service.updateStatus(ADMIN, 42L, new UpdateStatusRequest(ShipmentStatus.DELIVERED, null, null)))
                .satisfies(e -> assertCode(e, ShipmentErrorCode.INVALID_STATUS_TRANSITION))
                .hasMessageContaining("Cannot move shipment from CREATED to DELIVERED")
                .hasMessageContaining("next allowed: CANCELLED, PICKED_UP");
    }

    @Test
    void finalStatusCannotChange() {
        stub(shipment(ShipmentStatus.DELIVERED, 10L));
        assertThatThrownBy(() -> service.updateStatus(ADMIN, 42L, new UpdateStatusRequest(ShipmentStatus.CANCELLED, null, null)))
                .satisfies(e -> assertCode(e, ShipmentErrorCode.INVALID_STATUS_TRANSITION))
                .hasMessageContaining("DELIVERED is final");
    }

    @Test
    void cannotPickUpWithoutDriver() {
        stub(shipment(ShipmentStatus.CREATED, null));
        assertThatThrownBy(() -> service.updateStatus(ADMIN, 42L, new UpdateStatusRequest(ShipmentStatus.PICKED_UP, null, null)))
                .satisfies(e -> assertCode(e, ShipmentErrorCode.DRIVER_NOT_ASSIGNED));
    }

    @Test
    void deliveryPublishesEventForBilling() {
        Shipment s = stub(shipment(ShipmentStatus.IN_TRANSIT, 10L));

        service.updateStatus(DRIVER, 42L, new UpdateStatusRequest(ShipmentStatus.DELIVERED, null, null));

        verify(events).publishEvent(new ShipmentDeliveredEvent(42L));
        assertThat(s.getDeliveredAt()).isEqualTo(Instant.parse("2026-11-04T09:30:00Z"));
    }

    @Test
    void driverCannotCancelAndCustomerCannotDeliver() {
        stub(shipment(ShipmentStatus.CREATED, 10L));
        assertThatThrownBy(() -> service.updateStatus(DRIVER, 42L, new UpdateStatusRequest(ShipmentStatus.CANCELLED, null, null)))
                .satisfies(e -> assertCode(e, CommonErrorCode.FORBIDDEN));
        assertThatThrownBy(() -> service.updateStatus(OWNER, 42L, new UpdateStatusRequest(ShipmentStatus.PICKED_UP, null, null)))
                .satisfies(e -> assertCode(e, CommonErrorCode.FORBIDDEN));
    }

    @Test
    void unassignedDriverCannotTouchShipment() {
        stub(shipment(ShipmentStatus.CREATED, 10L));
        assertThatThrownBy(() -> service.updateStatus(OTHER_DRIVER, 42L, new UpdateStatusRequest(ShipmentStatus.PICKED_UP, null, null)))
                .satisfies(e -> assertCode(e, CommonErrorCode.FORBIDDEN));
    }

    @Test
    void ownerCanCancelBeforePickup() {
        stub(shipment(ShipmentStatus.CREATED, null));
        var response = service.updateStatus(OWNER, 42L, new UpdateStatusRequest(ShipmentStatus.CANCELLED, "changed my mind", null));
        assertThat(response.status()).isEqualTo(ShipmentStatus.CANCELLED);
    }

    @Test
    void staleExpectedVersionIs409() {
        Shipment s = stub(shipment(ShipmentStatus.CREATED, 10L));
        ReflectionTestUtils.setField(s, "version", 3L);
        assertThatThrownBy(() -> service.updateStatus(DRIVER, 42L, new UpdateStatusRequest(ShipmentStatus.PICKED_UP, null, 2L)))
                .satisfies(e -> assertCode(e, ShipmentErrorCode.VERSION_MISMATCH));
    }

    // ---------------------------------------------------------------- driver assignment

    @Test
    void assignDriverChecksRoleWithAuthService() {
        stub(shipment(ShipmentStatus.CREATED, null));
        when(authClient.findUser(5L)).thenReturn(Optional.of(new AuthClient.UserView(5L, "c@x.com", "C", Role.CUSTOMER)));
        assertThatThrownBy(() -> service.assignDriver(ADMIN, 42L, 5L))
                .satisfies(e -> assertCode(e, ShipmentErrorCode.INVALID_DRIVER))
                .hasMessageContaining("is a CUSTOMER");

        when(authClient.findUser(6L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.assignDriver(ADMIN, 42L, 6L)).satisfies(e -> assertCode(e, ShipmentErrorCode.INVALID_DRIVER));

        when(authClient.findUser(10L)).thenReturn(Optional.of(new AuthClient.UserView(10L, "d@x.com", "D", Role.DRIVER)));
        assertThat(service.assignDriver(ADMIN, 42L, 10L).driverId()).isEqualTo(10L);
    }

    @Test
    void cannotAssignDriverToClosedShipment() {
        stub(shipment(ShipmentStatus.DELIVERED, 10L));
        assertThatThrownBy(() -> service.assignDriver(ADMIN, 42L, 10L)).satisfies(e -> assertCode(e, ShipmentErrorCode.SHIPMENT_CLOSED));
        verify(authClient, never()).findUser(any());
    }

    // ---------------------------------------------------------------- idempotency (FR-10)

    @Test
    void sameKeySameBodyReplaysFirstShipment() {
        CreateShipmentRequest request = request("2.5");
        Shipment existing = shipment(ShipmentStatus.CREATED, null);
        existing.useIdempotencyKey("key-1", ShipmentService.fingerprint(request("2.500")));
        when(shipments.findByCustomerIdAndIdempotencyKey(1L, "key-1")).thenReturn(Optional.of(existing));

        var result = service.create(OWNER, request, "key-1");

        assertThat(result.replayed()).isTrue();
        assertThat(result.shipment().id()).isEqualTo(42L);
        verify(shipments, never()).saveAndFlush(any());
    }

    @Test
    void sameKeyDifferentBodyIs422() {
        Shipment existing = shipment(ShipmentStatus.CREATED, null);
        existing.useIdempotencyKey("key-1", ShipmentService.fingerprint(request("2.5")));
        when(shipments.findByCustomerIdAndIdempotencyKey(1L, "key-1")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.create(OWNER, request("9"), "key-1"))
                .satisfies(e -> assertCode(e, ShipmentErrorCode.IDEMPOTENCY_KEY_REUSED));
    }

    @Test
    void trackingCodeFormat() {
        assertThat(ShipmentService.trackingCode(42L, Instant.parse("2026-11-04T09:30:00Z"))).isEqualTo("ST-2026-000042");
        assertThat(ShipmentService.trackingCode(1234567L, Instant.parse("2027-01-01T00:00:00Z"))).isEqualTo("ST-2027-1234567");
    }

    // ---------------------------------------------------------------- helpers

    private Shipment stub(Shipment s) {
        lenient().when(shipments.findById(42L)).thenReturn(Optional.of(s));
        return s;
    }

    private static Shipment shipment(ShipmentStatus status, Long driverId) {
        Shipment s = new Shipment(1L, "Sara", "+92 300 1234567", "Islamabad", "Ali", "+92 321 7654321", "Karachi",
                new BigDecimal("2.5"), null);
        ReflectionTestUtils.setField(s, "id", 42L);
        ReflectionTestUtils.setField(s, "status", status);
        ReflectionTestUtils.setField(s, "createdAt", Instant.parse("2026-11-04T09:00:00Z"));
        if (driverId != null) {
            s.assignDriver(driverId);
        }
        return s;
    }

    private static CreateShipmentRequest request(String weight) {
        return new CreateShipmentRequest("Sara", "+92 300 1234567", "Islamabad", "Ali", "+92 321 7654321", "Karachi",
                new BigDecimal(weight), null);
    }

    private static void assertCode(Throwable e, com.shiptrack.common.error.ErrorCode code) {
        assertThat(e).isInstanceOf(ApiException.class);
        assertThat(((ApiException) e).getCode()).isEqualTo(code);
    }
}
