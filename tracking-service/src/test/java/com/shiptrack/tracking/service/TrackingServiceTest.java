package com.shiptrack.tracking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.shiptrack.tracking.client.ShipmentClient;
import com.shiptrack.tracking.exception.TrackingException;
import com.shiptrack.tracking.repository.LocationRepository;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import java.time.Clock;
import org.junit.jupiter.api.Test;

class TrackingServiceTest {

    private final ShipmentClient client = mock(ShipmentClient.class);
    private final TrackingService service = new TrackingService(client, mock(LocationRepository.class), Clock.systemUTC());

    private TrackingException loadFails() {
        return catchThrowableOfType(TrackingException.class, () -> service.loadShipment(1L, "Bearer x", "corr"));
    }

    private void shipmentServiceAnswers(HttpStatus status) {
        when(client.getShipment(eq(1L), any(), any()))
                .thenThrow(new HttpClientResponseException("refused", HttpResponse.status(status)));
    }

    @Test
    void anEmptyAnswerMeansNotFound() {
        when(client.getShipment(eq(1L), any(), any())).thenReturn(null);

        assertThat(loadFails().getStatus().getCode()).isEqualTo(404);
    }

    @Test
    void anExpiredTokenStaysA401() {
        shipmentServiceAnswers(HttpStatus.UNAUTHORIZED);

        TrackingException e = loadFails();
        assertThat(e.getStatus().getCode()).isEqualTo(401);
        assertThat(e.getCode()).isEqualTo("UNAUTHORIZED");
    }

    @Test
    void aServerFailureMeansTheDependencyIsUnavailable() {
        shipmentServiceAnswers(HttpStatus.INTERNAL_SERVER_ERROR);

        assertThat(loadFails().getStatus().getCode()).isEqualTo(503);
    }
}
