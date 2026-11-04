package com.shiptrack.shipment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shiptrack.shipment.client.BillingClient;
import com.shiptrack.shipment.entity.Shipment;
import com.shiptrack.shipment.entity.ShipmentStatus;
import com.shiptrack.shipment.repository.ShipmentRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.ResourceAccessException;

@ExtendWith(MockitoExtension.class)
class InvoiceRequesterTest {

    @Mock
    private ShipmentRepository shipments;
    @Mock
    private BillingClient billingClient;
    @Mock
    private TransactionTemplate tx;

    private CircuitBreaker breaker;
    private InvoiceRequester requester;

    @BeforeEach
    void setUp() {
        breaker = CircuitBreaker.of("billing", CircuitBreakerConfig.custom()
                .slidingWindowSize(4).minimumNumberOfCalls(4).failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofMinutes(5))
                .recordExceptions(ResourceAccessException.class)
                .build());
        requester = new InvoiceRequester(shipments, billingClient, breaker, tx, 3, Duration.ofMillis(10));
        lenient().doAnswer(inv -> {
            inv.<Consumer<TransactionStatus>>getArgument(0).accept(null);
            return null;
        }).when(tx).executeWithoutResult(any());
    }

    @Test
    void successMarksShipmentInvoiced() {
        stubDelivered(1L);
        doNothing().when(billingClient).createInvoice(any());

        assertThat(requester.requestInvoice(1L)).isTrue();
        verify(shipments).markInvoiced(1L);
    }

    @Test
    void billingDownLeavesShipmentForRetry() {
        stubDelivered(1L);
        doThrow(new ResourceAccessException("connection refused")).when(billingClient).createInvoice(any());

        assertThat(requester.requestInvoice(1L)).isFalse();
        verify(shipments, never()).markInvoiced(any());
    }

    @Test
    void circuitOpensAfterRepeatedFailuresAndStopsCallingBilling() {
        stubDelivered(1L);
        doThrow(new ResourceAccessException("down")).when(billingClient).createInvoice(any());

        for (int i = 0; i < 4; i++) {
            requester.requestInvoice(1L);
        }
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        assertThat(requester.requestInvoice(1L)).isFalse(); // fails fast
        verify(billingClient, times(4)).createInvoice(any()); // the 5th call never reached billing
    }

    @Test
    void retryJobStopsAtFirstFailureAndContinuesNextRound() {
        when(shipments.findUninvoicedDeliveredIds(any())).thenReturn(List.of(1L, 2L, 3L));
        stubDelivered(1L);
        stubDelivered(2L);
        doNothing().doThrow(new ResourceAccessException("down")).when(billingClient).createInvoice(any());

        requester.retryPendingInvoices();

        verify(shipments).markInvoiced(1L);
        verify(shipments, never()).markInvoiced(2L);
        verify(shipments, never()).findById(3L);
    }

    @Test
    void skipsAlreadyInvoicedOrNotDelivered() {
        Shipment notDelivered = shipment(5L, ShipmentStatus.IN_TRANSIT);
        when(shipments.findById(5L)).thenReturn(Optional.of(notDelivered));
        when(shipments.findById(6L)).thenReturn(Optional.empty());

        assertThat(requester.requestInvoice(5L)).isTrue();
        assertThat(requester.requestInvoice(6L)).isTrue();
        verify(billingClient, never()).createInvoice(any());
    }

    @Test
    void deliveredEventRetriesQuicklyThroughABlip() {
        stubDelivered(1L);
        doThrow(new ResourceAccessException("cold start")).doNothing().when(billingClient).createInvoice(any());

        requester.onDelivered(new ShipmentDeliveredEvent(1L));

        verify(billingClient, times(2)).createInvoice(any());
        verify(shipments).markInvoiced(1L);
    }

    @Test
    void deliveredEventGivesUpAfterImmediateAttempts() {
        stubDelivered(1L);
        doThrow(new ResourceAccessException("down")).when(billingClient).createInvoice(any());

        requester.onDelivered(new ShipmentDeliveredEvent(1L));

        verify(billingClient, times(3)).createInvoice(any());
        verify(shipments, never()).markInvoiced(any());
    }

    @Test
    void deliveredEventTriggersRequest() {
        stubDelivered(1L);
        requester.onDelivered(new ShipmentDeliveredEvent(1L));
        verify(billingClient).createInvoice(new BillingClient.CreateInvoiceRequest(1L, "ST-2026-000001", 7L, new BigDecimal("2.500")));
    }

    private void stubDelivered(Long id) {
        lenient().when(shipments.findById(id)).thenReturn(Optional.of(shipment(id, ShipmentStatus.DELIVERED)));
    }

    private static Shipment shipment(Long id, ShipmentStatus status) {
        Shipment s = new Shipment(7L, "Sara", "+92 300 1234567", "A", "Ali", "+92 321 7654321", "B", new BigDecimal("2.5"), null);
        org.springframework.test.util.ReflectionTestUtils.setField(s, "id", id);
        org.springframework.test.util.ReflectionTestUtils.setField(s, "status", status);
        s.assignTrackingCode("ST-2026-%06d".formatted(id));
        return s;
    }
}
