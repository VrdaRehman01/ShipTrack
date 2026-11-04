package com.shiptrack.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.shiptrack.common.security.JwtVerifier;
import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Jwts;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Real gateway on a random port, in front of a fake downstream service that echoes what it received. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiGatewayTest {

    private static final String SECRET = "gateway-test-secret-at-least-32-characters!!";
    private static final AtomicInteger DOWNSTREAM_CALLS = new AtomicInteger();
    private static final HttpServer DOWNSTREAM = startDownstream();

    @LocalServerPort
    private int port;

    private final HttpClient http = HttpClient.newHttpClient();

    @DynamicPropertySource
    static void routes(DynamicPropertyRegistry registry) {
        String url = "http://localhost:" + DOWNSTREAM.getAddress().getPort();
        registry.add("AUTH_SERVICE_URL", () -> url);
        registry.add("SHIPMENT_SERVICE_URL", () -> url);
        registry.add("BILLING_SERVICE_URL", () -> url);
        registry.add("TRACKING_SERVICE_URL", () -> "http://localhost:1"); // nothing listens here
        registry.add("JWT_SECRET", () -> SECRET);
    }

    @AfterAll
    static void stop() {
        DOWNSTREAM.stop(0);
    }

    @BeforeEach
    void reset() {
        DOWNSTREAM_CALLS.set(0);
    }

    @Test
    void publicRoutesNeedNoToken() throws Exception {
        HttpResponse<String> response = send(HttpRequest.newBuilder(uri("/api/v1/auth/login"))
                .POST(HttpRequest.BodyPublishers.ofString("{}")).header("Content-Type", "application/json"));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat((String) JsonPath.read(response.body(), "$.path")).isEqualTo("/api/v1/auth/login");
        assertThat((String) JsonPath.read(response.body(), "$.method")).isEqualTo("POST");
    }

    @Test
    void protectedRoutesAreRejectedAtTheEdgeWithoutToken() throws Exception {
        HttpResponse<String> response = send(HttpRequest.newBuilder(uri("/api/v1/shipments")).GET());

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat((String) JsonPath.read(response.body(), "$.error")).isEqualTo("UNAUTHORIZED");
        assertThat((String) JsonPath.read(response.body(), "$.path")).isEqualTo("/api/v1/shipments");
        assertThat(DOWNSTREAM_CALLS.get()).isZero();
    }

    @Test
    void validTokenIsForwardedWithCorrelationIdAndQuery() throws Exception {
        String token = token();
        HttpResponse<String> response = send(HttpRequest.newBuilder(uri("/api/v1/invoices?shipmentId=42"))
                .header("Authorization", "Bearer " + token).GET());

        assertThat(response.statusCode()).isEqualTo(200);
        String body = response.body();
        assertThat((String) JsonPath.read(body, "$.path")).isEqualTo("/api/v1/invoices");
        assertThat((String) JsonPath.read(body, "$.query")).isEqualTo("shipmentId=42");
        assertThat((String) JsonPath.read(body, "$.authorization")).isEqualTo("Bearer " + token);
        String correlationId = response.headers().firstValue("X-Correlation-Id").orElseThrow();
        assertThat((String) JsonPath.read(body, "$.correlationId")).isEqualTo(correlationId);
    }

    @Test
    void internalApisAreNeverExposed() throws Exception {
        HttpResponse<String> anonymous = send(HttpRequest.newBuilder(uri("/internal/invoices"))
                .POST(HttpRequest.BodyPublishers.ofString("{}")).header("X-Internal-Api-Key", "guess"));
        HttpResponse<String> withToken = send(HttpRequest.newBuilder(uri("/internal/invoices"))
                .header("Authorization", "Bearer " + token()).POST(HttpRequest.BodyPublishers.ofString("{}")));

        assertThat(anonymous.statusCode()).isIn(401, 403, 404);
        assertThat(withToken.statusCode()).isIn(403, 404);
        assertThat(DOWNSTREAM_CALLS.get()).isZero();
    }

    @Test
    void docsAreProxiedToEachServiceSpec() throws Exception {
        HttpResponse<String> response = send(HttpRequest.newBuilder(uri("/docs/auth")).GET());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat((String) JsonPath.read(response.body(), "$.path")).isEqualTo("/v3/api-docs");
    }

    @Test
    void downServiceGives503InStandardShape() throws Exception {
        HttpResponse<String> response = send(HttpRequest.newBuilder(uri("/api/v1/tracking/1/latest"))
                .header("Authorization", "Bearer " + token()).GET());

        assertThat(response.statusCode()).isEqualTo(503);
        assertThat((String) JsonPath.read(response.body(), "$.error")).isEqualTo("DEPENDENCY_UNAVAILABLE");
    }

    @Test
    void corsPreflightFromFrontendIsAllowed() throws Exception {
        HttpResponse<String> response = send(HttpRequest.newBuilder(uri("/api/v1/shipments"))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Origin", "http://localhost:4200")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "authorization,content-type,idempotency-key"));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).contains("http://localhost:4200");

        HttpResponse<String> evil = send(HttpRequest.newBuilder(uri("/api/v1/shipments"))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Origin", "https://evil.example")
                .header("Access-Control-Request-Method", "POST"));
        assertThat(evil.statusCode()).isEqualTo(403);
    }

    @Test
    void healthAndSwaggerUiArePublic() throws Exception {
        assertThat(send(HttpRequest.newBuilder(uri("/actuator/health")).GET()).statusCode()).isEqualTo(200);
        assertThat(send(HttpRequest.newBuilder(uri("/v3/api-docs/swagger-config")).GET()).body()).contains("/docs/shipments");
    }

    // ---------------------------------------------------------------- helpers

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws IOException, InterruptedException {
        return http.send(request.timeout(Duration.ofSeconds(20)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String token() {
        Instant now = Instant.now();
        return Jwts.builder().subject("7").issuer("shiptrack-auth")
                .claim(JwtVerifier.CLAIM_EMAIL, "u@example.com").claim(JwtVerifier.CLAIM_ROLE, "CUSTOMER")
                .issuedAt(Date.from(now)).expiration(Date.from(now.plus(Duration.ofMinutes(5))))
                .signWith(JwtVerifier.signingKey(SECRET)).compact();
    }

    /** Echoes method, path, query and the headers the gateway is responsible for. */
    private static HttpServer startDownstream() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/", exchange -> {
                DOWNSTREAM_CALLS.incrementAndGet();
                var h = exchange.getRequestHeaders();
                String json = """
                        {"method":"%s","path":"%s","query":"%s","authorization":"%s","correlationId":"%s"}""".formatted(
                        exchange.getRequestMethod(),
                        exchange.getRequestURI().getPath(),
                        exchange.getRequestURI().getRawQuery() == null ? "" : exchange.getRequestURI().getRawQuery(),
                        h.getFirst("Authorization") == null ? "" : h.getFirst("Authorization"),
                        h.getFirst("X-Correlation-Id") == null ? "" : h.getFirst("X-Correlation-Id"));
                byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
