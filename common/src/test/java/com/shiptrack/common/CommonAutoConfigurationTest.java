package com.shiptrack.common;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shiptrack.common.security.InternalApiKeyFilter;
import com.shiptrack.common.security.JwtVerifier;
import com.shiptrack.common.web.CorrelationIdFilter;
import io.jsonwebtoken.Jwts;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = TestApp.class, properties = {
    "shiptrack.jwt.secret=" + CommonAutoConfigurationTest.SECRET,
    "shiptrack.security.public-paths=/public/**",
    "shiptrack.security.internal-api-key=" + CommonAutoConfigurationTest.INTERNAL_KEY
})
@AutoConfigureMockMvc
class CommonAutoConfigurationTest {

    static final String SECRET = "common-test-secret-at-least-32-characters!!";
    static final String INTERNAL_KEY = "internal-test-key";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void validTokenAuthenticatesAndExposesTokenForForwarding() throws Exception {
        mockMvc.perform(get("/api/test/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token(5L, "DRIVER", "shiptrack-auth")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.role").value("DRIVER"))
                .andExpect(jsonPath("$.hasToken").value(true));
    }

    @Test
    void missingTokenIs401InStandardShape() throws Exception {
        mockMvc.perform(get("/api/test/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.path").value("/api/test/me"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    @Test
    void tokenFromWrongIssuerOrGarbageIs401() throws Exception {
        mockMvc.perform(get("/api/test/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token(5L, "ADMIN", "evil")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/test/me").header(HttpHeaders.AUTHORIZATION, "Bearer garbage"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/test/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token(5L, "SUPERUSER", "shiptrack-auth")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongRoleIs403() throws Exception {
        mockMvc.perform(get("/api/test/admin").header(HttpHeaders.AUTHORIZATION, "Bearer " + token(1L, "CUSTOMER", "shiptrack-auth")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
        mockMvc.perform(get("/api/test/admin").header(HttpHeaders.AUTHORIZATION, "Bearer " + token(1L, "ADMIN", "shiptrack-auth")))
                .andExpect(status().isOk());
    }

    @Test
    void correlationIdIsReusedOrGenerated() throws Exception {
        mockMvc.perform(get("/api/test/me").header(CorrelationIdFilter.HEADER, "abc-123")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(1L, "CUSTOMER", "shiptrack-auth")))
                .andExpect(header().string(CorrelationIdFilter.HEADER, "abc-123"))
                .andExpect(jsonPath("$.correlationId").value("abc-123"));
        // Unsafe values are replaced, not logged.
        mockMvc.perform(get("/public/hello").header(CorrelationIdFilter.HEADER, "bad value\nwith newline"))
                .andExpect(header().string(CorrelationIdFilter.HEADER, not(containsString(" "))));
    }

    @Test
    void publicPathNeedsNoToken() throws Exception {
        mockMvc.perform(get("/public/hello")).andExpect(status().isOk()).andExpect(content().string("hello"));
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void internalPathsNeedTheKeyAndRejectUserTokens() throws Exception {
        mockMvc.perform(get("/internal/ping").header(InternalApiKeyFilter.HEADER, INTERNAL_KEY))
                .andExpect(status().isOk()).andExpect(content().string("pong"));
        mockMvc.perform(get("/internal/ping").header(InternalApiKeyFilter.HEADER, "wrong"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/internal/ping").header(HttpHeaders.AUTHORIZATION, "Bearer " + token(1L, "ADMIN", "shiptrack-auth")))
                .andExpect(status().isForbidden());
    }

    @Test
    void errorsMapToStandardCodes() throws Exception {
        String auth = "Bearer " + token(1L, "CUSTOMER", "shiptrack-auth");
        mockMvc.perform(get("/api/test/boom").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message", not(containsString("secret"))));
        mockMvc.perform(get("/api/test/not-found").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("Thing not found: 7"));
        mockMvc.perform(get("/api/test/lock").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("CONCURRENT_MODIFICATION"));
        mockMvc.perform(get("/api/test/integrity").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("CONFLICT"));
        mockMvc.perform(get("/api/test/downstream").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.error").value("DEPENDENCY_UNAVAILABLE"));
        mockMvc.perform(get("/api/test/nope").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/test/boom").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(get("/api/test/number").param("value", "abc").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("MALFORMED_REQUEST"));
        mockMvc.perform(get("/api/test/number").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message", containsString("value")));
    }

    @Test
    void validationErrorsAreReadable() throws Exception {
        String auth = "Bearer " + token(1L, "CUSTOMER", "shiptrack-auth");
        mockMvc.perform(post("/api/test/validate").header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message", containsString("name")));
        mockMvc.perform(post("/api/test/validate").header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("MALFORMED_REQUEST"));
        mockMvc.perform(get("/api/test/page").param("size", "50").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("size")));
    }

    @Test
    void pageResponseHasStandardShape() throws Exception {
        mockMvc.perform(get("/api/test/page").header(HttpHeaders.AUTHORIZATION, "Bearer " + token(1L, "CUSTOMER", "shiptrack-auth")))
                .andExpect(jsonPath("$.content[0]").value("item-1"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3));
    }

    @Test
    void swaggerHasBearerScheme() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"));
    }

    private static String token(Long id, String role, String issuer) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(id))
                .issuer(issuer)
                .claim(JwtVerifier.CLAIM_EMAIL, "u@example.com")
                .claim(JwtVerifier.CLAIM_ROLE, role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(Duration.ofMinutes(5))))
                .signWith(JwtVerifier.signingKey(SECRET))
                .compact();
    }
}
