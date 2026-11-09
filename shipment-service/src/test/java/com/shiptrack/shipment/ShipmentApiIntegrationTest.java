package com.shiptrack.shipment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.shiptrack.common.security.JwtVerifier;
import com.shiptrack.common.security.Role;
import com.shiptrack.shipment.client.AuthClient;
import com.shiptrack.shipment.client.BillingClient;
import com.shiptrack.shipment.repository.ShipmentRepository;
import com.shiptrack.shipment.service.InvoiceRequester;
import io.jsonwebtoken.Jwts;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.ResourceAccessException;

/** The whole shipment flow through the real security chain, against a real MySQL. Other services are mocked. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ShipmentApiIntegrationTest {

    private static final String SECRET = "test-only-secret-at-least-32-characters-long!!";
    private static final AtomicLong IDS = new AtomicLong(1000);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ShipmentRepository shipments;
    @Autowired
    private InvoiceRequester invoiceRequester;

    @MockitoBean
    private BillingClient billingClient;
    @MockitoBean
    private AuthClient authClient;

    private long customerId;
    private long driverId;
    private String customer;
    private String driver;
    private final String admin = token(1L, Role.ADMIN);

    @BeforeEach
    void setUp() {
        customerId = IDS.incrementAndGet();
        driverId = IDS.incrementAndGet();
        customer = token(customerId, Role.CUSTOMER);
        driver = token(driverId, Role.DRIVER);
        when(authClient.findUser(driverId))
                .thenReturn(Optional.of(new AuthClient.UserView(driverId, "d@x.com", "Bilal Ahmed", Role.DRIVER, "+92 311 7654321")));
        when(authClient.currentUser())
                .thenReturn(new AuthClient.UserView(driverId, "d@x.com", "Bilal Ahmed", Role.DRIVER, "+92 311 7654321"));
    }

    @Test
    void driverSeesAnOpenParcelWithoutPersonalDataAcceptsItAndTheCustomerSeesWhoIsComing() throws Exception {
        long id = create(customer);

        String jobs = mockMvc.perform(get("/api/v1/shipments/available").param("size", "100")
                        .header(HttpHeaders.AUTHORIZATION, bearer(driver)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == %d)].pickupAddress".formatted(id)).value("F-7/2, Islamabad"))
                .andReturn().getResponse().getContentAsString();
        assertThat(jobs).doesNotContain("Sara Ahmed", "+92 300 1234567", "Ali Khan", "+92 321 7654321");

        String tomorrow = LocalDate.now().plusDays(1).toString();
        mockMvc.perform(post("/api/v1/shipments/{id}/accept", id).header(HttpHeaders.AUTHORIZATION, bearer(driver))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"expectedDeliveryDate\":\"" + tomorrow + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.driverId").value(driverId));

        mockMvc.perform(get("/api/v1/shipments/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.driverName").value("Bilal Ahmed"))
                .andExpect(jsonPath("$.driverPhone").value("+92 311 7654321"))
                .andExpect(jsonPath("$.expectedDeliveryDate").value(tomorrow));

        mockMvc.perform(get("/api/v1/shipments/available").param("size", "100").header(HttpHeaders.AUTHORIZATION, bearer(driver)))
                .andExpect(jsonPath("$.content[?(@.id == %d)]".formatted(id)).isEmpty());
    }

    @Test
    void aDriverCanGiveBackAParcelBeforePickupSoAnotherCanTakeIt() throws Exception {
        long id = create(customer);
        String other = token(IDS.incrementAndGet(), Role.DRIVER);
        mockMvc.perform(post("/api/v1/shipments/{id}/accept", id).header(HttpHeaders.AUTHORIZATION, bearer(driver)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/shipments/{id}/release", id).header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/shipments/{id}/release", id).header(HttpHeaders.AUTHORIZATION, bearer(driver)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.driverId").doesNotExist())
                .andExpect(jsonPath("$.driverName").doesNotExist());

        mockMvc.perform(post("/api/v1/shipments/{id}/accept", id).header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isOk());
    }

    @Test
    void aCollectedParcelCannotBeGivenBack() throws Exception {
        long id = create(customer);
        assign(id);
        patchStatus(driver, id, "PICKED_UP").andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/shipments/{id}/release", id).header(HttpHeaders.AUTHORIZATION, bearer(driver)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CANNOT_RELEASE_JOB"));
    }

    @Test
    void aParcelCanOnlyBeAcceptedOnce() throws Exception {
        long id = create(customer);
        String other = token(IDS.incrementAndGet(), Role.DRIVER);

        mockMvc.perform(post("/api/v1/shipments/{id}/accept", id).header(HttpHeaders.AUTHORIZATION, bearer(driver)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/shipments/{id}/accept", id).header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("JOB_TAKEN"));
    }

    @Test
    void onlyDriversCanSeeOrAcceptOpenParcels() throws Exception {
        long id = create(customer);

        mockMvc.perform(get("/api/v1/shipments/available").header(HttpHeaders.AUTHORIZATION, bearer(customer)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/shipments/{id}/accept", id).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isForbidden());
    }

    @Test
    void fullLifecycleFromCreateToInvoice() throws Exception {
        long id = create(customer);

        // FR-5: no skipping, and pickup needs a driver
        patchStatus(admin, id, "DELIVERED").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_STATUS_TRANSITION"))
                .andExpect(jsonPath("$.message", containsString("Cannot move shipment from CREATED to DELIVERED")));
        patchStatus(admin, id, "PICKED_UP").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("DRIVER_NOT_ASSIGNED"));

        // driver can't see it before being assigned
        mockMvc.perform(get("/api/v1/shipments/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(driver)))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/shipments/{id}/driver", id).header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"driverId\":" + driverId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.driverId").value(driverId));

        doNothing().when(billingClient).createInvoice(any());
        patchStatus(driver, id, "PICKED_UP").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PICKED_UP"));
        patchStatus(driver, id, "IN_TRANSIT").andExpect(status().isOk());
        patchStatus(driver, id, "DELIVERED").andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveredAt").isNotEmpty());

        // FR-7: invoice requested in the background after commit
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(shipments.findById(id).orElseThrow().isInvoiced()).isTrue());
        verify(billingClient).createInvoice(eq(new BillingClient.CreateInvoiceRequest(
                id, shipments.findById(id).orElseThrow().getTrackingCode(), customerId, new java.math.BigDecimal("2.500"))));

        mockMvc.perform(get("/api/v1/shipments/{id}/history", id).header(HttpHeaders.AUTHORIZATION, bearer(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[0].toStatus").value("CREATED"))
                .andExpect(jsonPath("$[3].toStatus").value("DELIVERED"))
                .andExpect(jsonPath("$[3].changedByRole").value("DRIVER"));

        patchStatus(admin, id, "CANCELLED").andExpect(status().isBadRequest());
    }

    @Test
    void deliverySucceedsEvenWhenBillingIsDownAndInvoiceIsRetriedLater() throws Exception {
        long id = create(customer);
        assign(id);
        doThrow(new ResourceAccessException("billing down")).when(billingClient).createInvoice(any());

        patchStatus(driver, id, "PICKED_UP").andExpect(status().isOk());
        patchStatus(driver, id, "IN_TRANSIT").andExpect(status().isOk());
        patchStatus(driver, id, "DELIVERED").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DELIVERED"));

        // 3 quick attempts right after delivery, then it's left to the retry job.
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> verify(billingClient, times(3)).createInvoice(any()));
        assertThat(shipments.findById(id).orElseThrow().isInvoiced()).isFalse();

        // Billing comes back; the retry job picks it up.
        reset(billingClient);
        doNothing().when(billingClient).createInvoice(any());
        invoiceRequester.retryPendingInvoices();
        assertThat(shipments.findById(id).orElseThrow().isInvoiced()).isTrue();
    }

    @Test
    void ownerCanOpenTheirShipment() throws Exception {
        long id = create(customer);

        mockMvc.perform(get("/api/v1/shipments/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.status").value("CREATED"));
    }

    @Test
    void customersOnlySeeTheirOwnShipments() throws Exception {
        long id = create(customer);
        String stranger = token(IDS.incrementAndGet(), Role.CUSTOMER);

        mockMvc.perform(get("/api/v1/shipments/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(stranger)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
        mockMvc.perform(get("/api/v1/shipments").header(HttpHeaders.AUTHORIZATION, bearer(stranger)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/v1/shipments").param("size", "5").header(HttpHeaders.AUTHORIZATION, bearer(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(id))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalPages").value(1));
        mockMvc.perform(get("/api/v1/shipments/{id}", 999_999).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isNotFound());
    }

    @Test
    void idempotencyKeyPreventsDuplicates() throws Exception {
        String key = UUID.randomUUID().toString();
        String first = mockMvc.perform(post("/api/v1/shipments").header(HttpHeaders.AUTHORIZATION, bearer(customer))
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(body("2.5")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(post("/api/v1/shipments").header(HttpHeaders.AUTHORIZATION, bearer(customer))
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(body("2.500")))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.id").value((Integer) JsonPath.read(first, "$.id")));

        mockMvc.perform(post("/api/v1/shipments").header(HttpHeaders.AUTHORIZATION, bearer(customer))
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(body("9")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error").value("IDEMPOTENCY_KEY_REUSED"));

        mockMvc.perform(get("/api/v1/shipments").header(HttpHeaders.AUTHORIZATION, bearer(customer)))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void onlyCustomersCreateAndInputIsValidated() throws Exception {
        mockMvc.perform(post("/api/v1/shipments").header(HttpHeaders.AUTHORIZATION, bearer(driver))
                        .contentType(MediaType.APPLICATION_JSON).content(body("1")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/shipments").header(HttpHeaders.AUTHORIZATION, bearer(customer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senderName\":\"\",\"senderPhone\":\"abc\",\"weightKg\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message", containsString("senderPhone")))
                .andExpect(jsonPath("$.message", containsString("weightKg")));
        mockMvc.perform(post("/api/v1/shipments").contentType(MediaType.APPLICATION_JSON).content(body("1")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void publicTrackingShowsStatusButNoPersonalData() throws Exception {
        long id = create(customer);
        String code = shipments.findById(id).orElseThrow().getTrackingCode();

        mockMvc.perform(get("/api/v1/public/shipments/{code}", code.toLowerCase()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingCode").value(code))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.timeline[0].status").value("CREATED"))
                .andExpect(jsonPath("$.receiverName").doesNotExist())
                .andExpect(jsonPath("$.deliveryAddress").doesNotExist())
                .andExpect(jsonPath("$.customerId").doesNotExist());
        mockMvc.perform(get("/api/v1/public/shipments/ST-2026-999999")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/public/shipments/nonsense")).andExpect(status().isBadRequest());
    }

    @Test
    void customerCanCancelBeforePickupOnly() throws Exception {
        long id = create(customer);
        patchStatus(customer, id, "CANCELLED").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        mockMvc.perform(patch("/api/v1/shipments/{id}/driver", id).header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"driverId\":" + driverId + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("SHIPMENT_CLOSED"));
    }

    @Test
    void staleVersionIsRejected() throws Exception {
        long id = create(customer);
        assign(id);
        mockMvc.perform(patch("/api/v1/shipments/{id}/status", id).header(HttpHeaders.AUTHORIZATION, bearer(driver))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"PICKED_UP\",\"expectedVersion\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("VERSION_MISMATCH"));
    }

    // ---------------------------------------------------------------- helpers

    private long create(String token) throws Exception {
        String response = mockMvc.perform(post("/api/v1/shipments").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body("2.5")))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, containsString("/api/v1/shipments/")))
                .andExpect(jsonPath("$.trackingCode", matchesPattern("ST-\\d{4}-\\d{6,}")))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.weightKg").value("2.500"))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private void assign(long id) throws Exception {
        mockMvc.perform(patch("/api/v1/shipments/{id}/driver", id).header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"driverId\":" + driverId + "}"))
                .andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions patchStatus(String token, long id, String status) throws Exception {
        return mockMvc.perform(patch("/api/v1/shipments/{id}/status", id).header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + status + "\"}"));
    }

    private static String body(String weight) {
        return """
                {"senderName":"Sara Ahmed","senderPhone":"+92 300 1234567","pickupAddress":"F-7/2, Islamabad",
                 "receiverName":"Ali Khan","receiverPhone":"+92 321 7654321","deliveryAddress":"Clifton, Karachi",
                 "weightKg":%s,"description":"Books"}""".formatted(weight);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String token(long id, Role role) {
        Instant now = Instant.now();
        return Jwts.builder().subject(String.valueOf(id)).issuer("shiptrack-auth")
                .claim(JwtVerifier.CLAIM_EMAIL, "u" + id + "@example.com").claim(JwtVerifier.CLAIM_ROLE, role.name())
                .issuedAt(Date.from(now)).expiration(Date.from(now.plus(Duration.ofMinutes(15))))
                .signWith(JwtVerifier.signingKey(SECRET)).compact();
    }
}
