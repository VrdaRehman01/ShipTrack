package com.shiptrack.shipment.service;

import static com.shiptrack.shipment.entity.ShipmentStatus.CANCELLED;
import static com.shiptrack.shipment.entity.ShipmentStatus.CREATED;
import static com.shiptrack.shipment.entity.ShipmentStatus.DELIVERED;
import static com.shiptrack.shipment.entity.ShipmentStatus.IN_TRANSIT;
import static com.shiptrack.shipment.entity.ShipmentStatus.PICKED_UP;
import static org.assertj.core.api.Assertions.assertThat;

import com.shiptrack.shipment.entity.ShipmentStatus;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** FR-5: forward one step at a time, no skipping, no going back. */
class ShipmentStatusTest {

    @ParameterizedTest(name = "{0} -> {1} allowed={2}")
    @CsvSource({
        "CREATED,PICKED_UP,true",
        "PICKED_UP,IN_TRANSIT,true",
        "IN_TRANSIT,DELIVERED,true",
        "CREATED,CANCELLED,true",
        // skipping
        "CREATED,IN_TRANSIT,false",
        "CREATED,DELIVERED,false",
        "PICKED_UP,DELIVERED,false",
        // backwards / same
        "PICKED_UP,CREATED,false",
        "DELIVERED,IN_TRANSIT,false",
        "IN_TRANSIT,IN_TRANSIT,false",
        // cancel only before pickup
        "PICKED_UP,CANCELLED,false",
        "IN_TRANSIT,CANCELLED,false",
        // final states
        "DELIVERED,CANCELLED,false",
        "CANCELLED,CREATED,false"
    })
    void transitions(ShipmentStatus from, ShipmentStatus to, boolean allowed) {
        assertThat(from.canMoveTo(to)).isEqualTo(allowed);
    }

    @ParameterizedTest
    @CsvSource({"DELIVERED,true", "CANCELLED,true", "CREATED,false", "PICKED_UP,false", "IN_TRANSIT,false"})
    void finalStates(ShipmentStatus status, boolean isFinal) {
        assertThat(status.isFinal()).isEqualTo(isFinal);
    }

    @ParameterizedTest
    @CsvSource({"PICKED_UP,true", "IN_TRANSIT,true", "DELIVERED,true", "CANCELLED,false", "CREATED,false"})
    void driverSteps(ShipmentStatus status, boolean driverStep) {
        assertThat(status.isDriverStep()).isEqualTo(driverStep);
        assertThat(CREATED.allowedNext()).containsExactlyInAnyOrder(PICKED_UP, CANCELLED);
        assertThat(IN_TRANSIT.allowedNext()).containsExactly(DELIVERED);
        assertThat(DELIVERED.allowedNext()).isEmpty();
        assertThat(CANCELLED.allowedNext()).isEmpty();
    }
}
