package com.shiptrack.tracking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.shiptrack.tracking.client.ShipmentClient;
import com.shiptrack.tracking.client.ShipmentView;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.mysql.MySQLContainer;

@MicronautTest(transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrackingApiIntegrationTest implements TestPropertyProvider {

    private static final String SECRET = "test-only-secret-at-least-32-characters-long!!";
    private static final AtomicLong IDS = new AtomicLong(100);
    // Timeouts: a stalled readiness probe fails fast and retries instead of hanging (see TROUBLESHOOTING.md).
    private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withUrlParam("connectTimeout", "5000")
            .withUrlParam("socketTimeout", "30000")
            .withStartupTimeout(Duration.ofMinutes(3));

    @Inject
    @Client("/")
    HttpClient http;

    @Inject
    ShipmentClient shipmentClient;

    @MockBean(ShipmentClient.class)
    ShipmentClient mockShipmentClient() {
        return mock(ShipmentClient.class);
    }

    @Override
    public Map<String, String> getProperties() {
        MYSQL.start();
        return Map.of(
                "datasources.default.url", MYSQL.getJdbcUrl(),
                "datasources.default.username", MYSQL.getUsername(),
                "datasources.default.password", MYSQL.getPassword());
    }

    @Test
    void assignedDriverPostsAndOwnerReadsLatestAndRoute() {
        long shipmentId = IDS.incrementAndGet();
        long customerId = IDS.incrementAndGet();
        long driverId = IDS.incrementAndGet();
        stubShipment(shipmentId, customerId, driverId, "IN_TRANSIT");
        String driver = token(driverId, "DRIVER");

        String firstReading = "{\"latitude\":24.8609,\"longitude\":67.01,\"recordedAt\":\"" + Instant.now().minusSeconds(60) + "\"}";
        HttpResponse<Map> created = http.toBlocking().exchange(post(shipmentId, firstReading, driver), Map.class);
        assertThat(created.code()).isEqualTo(HttpStatus.CREATED.getCode());
        assertThat(created.header("X-Correlation-Id")).isNotBlank();
        http.toBlocking().exchange(post(shipmentId, "{\"latitude\":25.0,\"longitude\":67.5}", driver), Map.class);

        String owner = token(customerId, "CUSTOMER");
        Map<?, ?> latest = http.toBlocking().retrieve(get("/api/v1/tracking/" + shipmentId + "/latest", owner), Map.class);
        assertThat(latest.get("latitude")).hasToString("25.0");
        assertThat(latest.get("driverId")).hasToString(String.valueOf(driverId));
        assertThat(latest.get("recordedAt").toString()).endsWith("Z");

        List<Map> route = http.toBlocking().retrieve(get("/api/v1/tracking/" + shipmentId + "/locations?limit=5", owner),
                Argument.listOf(Map.class));
        assertThat(route).hasSize(2);
        assertThat(route.get(0).get("latitude")).hasToString("25.0");
    }

    @Test
    void onlyTheAssignedDriverCanPost() {
        long shipmentId = IDS.incrementAndGet();
        stubShipment(shipmentId, 1L, 999L, "IN_TRANSIT");
        Map<?, ?> error = expectError(post(shipmentId, "{\"latitude\":1,\"longitude\":1}", token(IDS.incrementAndGet(), "DRIVER")),
                HttpStatus.FORBIDDEN);
        assertThat(error.get("error")).isEqualTo("FORBIDDEN");
    }

    @Test
    void postingNeedsShipmentInProgress() {
        long shipmentId = IDS.incrementAndGet();
        long driverId = IDS.incrementAndGet();
        stubShipment(shipmentId, 1L, driverId, "CREATED");
        Map<?, ?> error = expectError(post(shipmentId, "{\"latitude\":1,\"longitude\":1}", token(driverId, "DRIVER")), HttpStatus.CONFLICT);
        assertThat(error.get("error")).isEqualTo("SHIPMENT_NOT_IN_PROGRESS");
    }

    @Test
    void customersCannotPostAndAnonymousIs401InStandardShape() {
        long shipmentId = IDS.incrementAndGet();
        Map<?, ?> forbidden = expectError(post(shipmentId, "{\"latitude\":1,\"longitude\":1}", token(5L, "CUSTOMER")),
                HttpStatus.FORBIDDEN);
        assertThat(forbidden.get("error")).isEqualTo("FORBIDDEN");

        Map<?, ?> unauthorized = expectError(HttpRequest.GET("/api/v1/tracking/1/latest").header("X-Correlation-Id", "trk-1"),
                HttpStatus.UNAUTHORIZED);
        assertThat(unauthorized.get("error")).isEqualTo("UNAUTHORIZED");
        assertThat(unauthorized.get("status")).hasToString("401");
        assertThat(unauthorized.get("path")).isEqualTo("/api/v1/tracking/1/latest");
        assertThat(unauthorized.get("correlationId")).isEqualTo("trk-1");

        Map<?, ?> expired = expectError(get("/api/v1/tracking/1/latest", token(5L, "CUSTOMER", Instant.now().minusSeconds(60))),
                HttpStatus.UNAUTHORIZED);
        assertThat(expired.get("error")).isEqualTo("UNAUTHORIZED");
    }

    @Test
    void invalidCoordinatesAreRejected() {
        long driverId = IDS.incrementAndGet();
        Map<?, ?> error = expectError(post(1L, "{\"latitude\":91,\"longitude\":0}", token(driverId, "DRIVER")), HttpStatus.BAD_REQUEST);
        assertThat(error.get("error")).isEqualTo("VALIDATION_FAILED");
        assertThat(error.get("message").toString()).contains("latitude");

        long shipmentId = IDS.incrementAndGet();
        stubShipment(shipmentId, 1L, driverId, "PICKED_UP");
        String future = Instant.now().plus(1, ChronoUnit.HOURS).toString();
        Map<?, ?> futureError = expectError(post(shipmentId, "{\"latitude\":1,\"longitude\":1,\"recordedAt\":\"" + future + "\"}",
                token(driverId, "DRIVER")), HttpStatus.BAD_REQUEST);
        assertThat(futureError.get("error")).isEqualTo("INVALID_RECORDED_AT");
    }

    @Test
    void accessDecisionsComeFromShipmentService() {
        long shipmentId = IDS.incrementAndGet();
        String someone = token(IDS.incrementAndGet(), "CUSTOMER");

        when(shipmentClient.getShipment(eq(shipmentId), anyString(), any()))
                .thenThrow(new HttpClientResponseException("Forbidden", HttpResponse.status(HttpStatus.FORBIDDEN)));
        assertThat(expectError(get("/api/v1/tracking/" + shipmentId + "/latest", someone), HttpStatus.FORBIDDEN).get("error"))
                .isEqualTo("FORBIDDEN");

        long missing = IDS.incrementAndGet();
        when(shipmentClient.getShipment(eq(missing), anyString(), any()))
                .thenThrow(new HttpClientResponseException("Not Found", HttpResponse.status(HttpStatus.NOT_FOUND)));
        assertThat(expectError(get("/api/v1/tracking/" + missing + "/latest", someone), HttpStatus.NOT_FOUND).get("error"))
                .isEqualTo("NOT_FOUND");

        long down = IDS.incrementAndGet();
        when(shipmentClient.getShipment(eq(down), anyString(), any())).thenThrow(new HttpClientException("Connection refused"));
        assertThat(expectError(get("/api/v1/tracking/" + down + "/latest", someone), HttpStatus.SERVICE_UNAVAILABLE).get("error"))
                .isEqualTo("DEPENDENCY_UNAVAILABLE");
    }

    @Test
    void latestBeforeAnyLocationIs404() {
        long shipmentId = IDS.incrementAndGet();
        long customerId = IDS.incrementAndGet();
        stubShipment(shipmentId, customerId, null, "CREATED");
        Map<?, ?> error = expectError(get("/api/v1/tracking/" + shipmentId + "/latest", token(customerId, "CUSTOMER")),
                HttpStatus.NOT_FOUND);
        assertThat(error.get("error")).isEqualTo("NO_LOCATION_YET");
    }

    @Test
    void healthAndSwaggerArePublic() {
        assertThat(http.toBlocking().exchange(HttpRequest.GET("/health")).code()).isEqualTo(200);
        String spec = http.toBlocking().retrieve(HttpRequest.GET("/swagger/tracking-service-v1.yml"));
        assertThat(spec).contains("/api/v1/tracking/{shipmentId}/locations");
    }

    // ---------------------------------------------------------------- helpers

    private void stubShipment(long shipmentId, long customerId, Long driverId, String status) {
        when(shipmentClient.getShipment(eq(shipmentId), anyString(), any()))
                .thenReturn(new ShipmentView(shipmentId, "ST-2026-%06d".formatted(shipmentId), status, customerId, driverId));
    }

    private Map<?, ?> expectError(HttpRequest<?> request, HttpStatus expected) {
        HttpClientResponseException e = catchThrowableOfType(HttpClientResponseException.class,
                () -> http.toBlocking().exchange(request, Map.class));
        assertThat(e).as("expected HTTP " + expected).isNotNull();
        assertThat(e.getStatus().getCode()).isEqualTo(expected.getCode());
        return e.getResponse().getBody(Map.class).orElseThrow();
    }

    private static MutableHttpRequest<String> post(long shipmentId, String json, String token) {
        return HttpRequest.POST("/api/v1/tracking/" + shipmentId + "/locations", json).bearerAuth(token)
                .contentType("application/json");
    }

    private static MutableHttpRequest<?> get(String uri, String token) {
        return HttpRequest.GET(uri).bearerAuth(token);
    }

    private static String token(long id, String role) {
        return token(id, role, Instant.now().plus(15, ChronoUnit.MINUTES));
    }

    private static String token(long id, String role, Instant expiresAt) {
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), new JWTClaimsSet.Builder()
                    .subject(String.valueOf(id)).issuer("shiptrack-auth")
                    .claim("email", "u" + id + "@example.com").claim("role", role)
                    .issueTime(new Date()).expirationTime(Date.from(expiresAt)).build());
            jwt.sign(new MACSigner(SECRET.getBytes(StandardCharsets.UTF_8)));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }
}
