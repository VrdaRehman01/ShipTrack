package com.shiptrack.billing;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.shiptrack.common.security.JwtVerifier;
import com.shiptrack.common.security.Role;
import io.jsonwebtoken.Jwts;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class BillingApiIntegrationTest {

    private static final String SECRET = "test-only-secret-at-least-32-characters-long!!";
    private static final String INTERNAL_KEY = "test-internal-key";
    private static final AtomicLong IDS = new AtomicLong(5000);

    @Autowired
    private MockMvc mockMvc;

    private final String admin = token(1L, Role.ADMIN);

    @Test
    void invoiceIsCreatedOnceAndPricedCorrectly() throws Exception {
        long shipmentId = IDS.incrementAndGet();
        long customerId = IDS.incrementAndGet();

        internalCreate(shipmentId, customerId, "2.5")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.invoiceNumber", matchesPattern("INV-\\d{4}-\\d{6,}")))
                .andExpect(jsonPath("$.amount").value("550.00"))
                .andExpect(jsonPath("$.baseFee").value("250.00"))
                .andExpect(jsonPath("$.weightCharge").value("300.00"))
                .andExpect(jsonPath("$.currency").value("PKR"))
                .andExpect(jsonPath("$.status").value("ISSUED"));

        // Retry from shipment-service: same invoice, no duplicate.
        internalCreate(shipmentId, customerId, "2.5").andExpect(status().isOk()).andExpect(jsonPath("$.amount").value("550.00"));
        // Same shipment, different customer: refuse.
        internalCreate(shipmentId, customerId + 1, "2.5").andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("INVOICE_MISMATCH"));
    }

    @Test
    void ownerAndAdminCanReadInvoiceOthersCannot() throws Exception {
        long shipmentId = IDS.incrementAndGet();
        long customerId = IDS.incrementAndGet();
        internalCreate(shipmentId, customerId, "1").andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/invoices").param("shipmentId", String.valueOf(shipmentId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(token(customerId, Role.CUSTOMER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shipmentId").value(shipmentId))
                .andExpect(jsonPath("$.amount").value("370.00"));
        mockMvc.perform(get("/api/v1/invoices").param("shipmentId", String.valueOf(shipmentId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/invoices").param("shipmentId", String.valueOf(shipmentId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(token(IDS.incrementAndGet(), Role.CUSTOMER))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/invoices").param("shipmentId", "987654321")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isNotFound());
    }

    @Test
    void listIsScopedAndPaymentIsAdminOnly() throws Exception {
        long customerId = IDS.incrementAndGet();
        String customer = token(customerId, Role.CUSTOMER);
        String body = internalCreate(IDS.incrementAndGet(), customerId, "3").andReturn().getResponse().getContentAsString();
        long invoiceId = ((Number) JsonPath.read(body, "$.id")).longValue();
        internalCreate(IDS.incrementAndGet(), customerId, "4");

        mockMvc.perform(get("/api/v1/invoices").header(HttpHeaders.AUTHORIZATION, bearer(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get("/api/v1/invoices").header(HttpHeaders.AUTHORIZATION, bearer(token(9L, Role.DRIVER))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/invoices/{id}/payments", invoiceId).header(HttpHeaders.AUTHORIZATION, bearer(customer)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/invoices/{id}/payments", invoiceId).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.paidAt").isNotEmpty());
        mockMvc.perform(post("/api/v1/invoices/{id}/payments", invoiceId).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("INVOICE_ALREADY_PAID"));

        mockMvc.perform(get("/api/v1/invoices").param("status", "PAID").header(HttpHeaders.AUTHORIZATION, bearer(customer)))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/v1/invoices/{id}", invoiceId).header(HttpHeaders.AUTHORIZATION, bearer(customer)))
                .andExpect(status().isOk());
    }

    @Test
    void internalApiNeedsKeyAndValidBody() throws Exception {
        mockMvc.perform(post("/internal/invoices").contentType(MediaType.APPLICATION_JSON)
                        .content(invoiceJson(1L, 1L, "1")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/internal/invoices").header("X-Internal-Api-Key", INTERNAL_KEY)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"shipmentId\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
        // A user token is not enough for internal endpoints.
        mockMvc.perform(post("/internal/invoices").header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(invoiceJson(1L, 1L, "1")))
                .andExpect(status().isForbidden());
        // ...and internal endpoints are not in public Swagger.
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/internal/invoices']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/invoices']").exists());
    }

    private ResultActions internalCreate(long shipmentId, long customerId, String weight) throws Exception {
        return mockMvc.perform(post("/internal/invoices").header("X-Internal-Api-Key", INTERNAL_KEY)
                .contentType(MediaType.APPLICATION_JSON).content(invoiceJson(shipmentId, customerId, weight)));
    }

    private static String invoiceJson(long shipmentId, long customerId, String weight) {
        return """
                {"shipmentId":%d,"trackingCode":"ST-2026-%06d","customerId":%d,"weightKg":%s}"""
                .formatted(shipmentId, shipmentId, customerId, weight);
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
