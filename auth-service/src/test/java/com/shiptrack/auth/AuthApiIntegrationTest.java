package com.shiptrack.auth;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** End-to-end through the real security chain against a real MySQL (Testcontainers). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AuthApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void registerThenLoginThenCallProtectedEndpoint() throws Exception {
        String email = uniqueEmail();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(email, "S3cure-pass!", "Ali Khan")))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, containsString("/api/v1/users/")))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());

        String token = login(email, "S3cure-pass!");

        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void duplicateEmailReturns409InStandardErrorShape() throws Exception {
        String email = uniqueEmail();
        register(email);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Correlation-Id", "test-corr-1")
                        .content(registerJson(email.toUpperCase(), "S3cure-pass!", "Someone Else")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("EMAIL_ALREADY_REGISTERED"))
                .andExpect(jsonPath("$.path").value("/api/v1/auth/register"))
                .andExpect(jsonPath("$.correlationId").value("test-corr-1"))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }

    @Test
    void invalidRegisterBodyReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("not-an-email", "short", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message", containsString("email")))
                .andExpect(jsonPath("$.message", containsString("password")))
                .andExpect(jsonPath("$.message", containsString("fullName")));
    }

    @Test
    void wrongPasswordReturns401() throws Exception {
        String email = uniqueEmail();
        register(email);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"wrong-password"}""".formatted(email)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"));
    }

    @Test
    void protectedEndpointWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.path").value("/api/v1/users/me"));
    }

    @Test
    void protectedEndpointWithBadTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshSwapsAValidTokenForOneThatWorks() throws Exception {
        String email = uniqueEmail();
        register(email);
        String token = login(email, "S3cure-pass!");

        String body = mockMvc.perform(post("/api/v1/auth/refresh").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.user.email").value(email))
                .andReturn().getResponse().getContentAsString();
        String fresh = JsonPath.read(body, "$.accessToken");

        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + fresh))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void refreshNeedsAValidToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/refresh").header(HttpHeaders.AUTHORIZATION, "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customerCannotCreateUsers() throws Exception {
        String email = uniqueEmail();
        register(email);
        String customerToken = login(email, "S3cure-pass!");

        mockMvc.perform(post("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createUserJson(uniqueEmail(), "DRIVER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void bootstrapAdminCanCreateDriverWhoCanLogIn() throws Exception {
        String adminToken = login("admin@shiptrack.test", "Admin-pass-123");
        String driverEmail = uniqueEmail();

        mockMvc.perform(post("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createUserJson(driverEmail, "DRIVER")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("DRIVER"));

        String driverToken = login(driverEmail, "Dr1ver-pass!");
        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + driverToken))
                .andExpect(jsonPath("$.role").value("DRIVER"));
    }

    @Test
    void adminCanListUsersByRoleInStandardPageShape() throws Exception {
        String adminToken = login("admin@shiptrack.test", "Admin-pass-123");

        mockMvc.perform(get("/api/v1/users").param("role", "ADMIN").param("size", "5")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].role").value("ADMIN"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalElements", notNullValue()))
                .andExpect(jsonPath("$.totalPages", notNullValue()));

        mockMvc.perform(get("/api/v1/users").param("size", "500")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
    }

    @Test
    void adminCanSaveADriversPhoneAndABadOneIsRejected() throws Exception {
        String adminToken = login("admin@shiptrack.test", "Admin-pass-123");

        mockMvc.perform(post("/api/v1/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Dr1ver-pass!","fullName":"Bilal","role":"DRIVER","phone":"+92 300 1234567"}"""
                                .formatted(uniqueEmail())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.phone").value("+92 300 1234567"));

        mockMvc.perform(post("/api/v1/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Dr1ver-pass!","fullName":"Bilal","role":"DRIVER","phone":"call me"}"""
                                .formatted(uniqueEmail())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("phone")));
    }

    private String firstAdminId(String adminToken) throws Exception {
        String list = mockMvc.perform(get("/api/v1/users").param("role", "ADMIN")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andReturn().getResponse().getContentAsString();
        return String.valueOf((Integer) JsonPath.read(list, "$.content[0].id"));
    }

    @Test
    void adminCanAddAPhoneToAnExistingPersonAndRemoveIt() throws Exception {
        String adminToken = login("admin@shiptrack.test", "Admin-pass-123");
        String id = firstAdminId(adminToken);

        mockMvc.perform(patch("/api/v1/users/{id}", id).header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\" ShipTrack Admin \",\"phone\":\"+92 300 1234567\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("ShipTrack Admin"))
                .andExpect(jsonPath("$.phone").value("+92 300 1234567"));
        mockMvc.perform(get("/api/v1/users/{id}", id).header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(jsonPath("$.phone").value("+92 300 1234567"));

        mockMvc.perform(patch("/api/v1/users/{id}", id).header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fullName\":\"ShipTrack Admin\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").doesNotExist());
    }

    @Test
    void editingPeopleIsValidatedAndAdminOnly() throws Exception {
        String adminToken = login("admin@shiptrack.test", "Admin-pass-123");
        String email = uniqueEmail();
        register(email);
        String customerToken = login(email, "S3cure-pass!");
        String body = "{\"fullName\":\"Someone\"}";

        mockMvc.perform(patch("/api/v1/users/{id}", 1).header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/users/{id}", 999_999).header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/v1/users/{id}", 1).header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fullName\":\"\",\"phone\":\"nope\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminCanOpenAnyUser() throws Exception {
        String adminToken = login("admin@shiptrack.test", "Admin-pass-123");
        String list = mockMvc.perform(get("/api/v1/users").param("role", "ADMIN")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andReturn().getResponse().getContentAsString();
        Integer id = JsonPath.read(list, "$.content[0].id");

        mockMvc.perform(get("/api/v1/users/{id}", id).header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
        mockMvc.perform(get("/api/v1/users/{id}", 999_999).header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void internalPathsAreClosedWithoutKey() throws Exception {
        mockMvc.perform(get("/internal/anything")).andExpect(status().isUnauthorized());
    }

    @Test
    void healthAndSwaggerArePublic() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/auth/login']").exists());
    }

    // --- helpers ---

    private void register(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(email, "S3cure-pass!", "Test User")))
                .andExpect(status().isCreated());
    }

    private String login(String email, String password) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}""".formatted(email, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    private static String registerJson(String email, String password, String fullName) {
        return """
                {"email":"%s","password":"%s","fullName":"%s"}""".formatted(email, password, fullName);
    }

    private static String createUserJson(String email, String role) {
        return """
                {"email":"%s","password":"Dr1ver-pass!","fullName":"Bilal Ahmed","role":"%s"}"""
                .formatted(email, role);
    }
}
