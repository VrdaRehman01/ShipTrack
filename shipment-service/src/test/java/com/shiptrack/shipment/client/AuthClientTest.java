package com.shiptrack.shipment.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.shiptrack.common.error.ApiException;
import com.shiptrack.common.error.CommonErrorCode;
import com.shiptrack.common.error.ErrorCode;
import com.shiptrack.common.security.AuthenticatedUser;
import com.shiptrack.common.security.Role;
import java.io.IOException;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AuthClientTest {

    private static final String USER_URL = "http://auth/api/v1/users/9";

    private final RestClient.Builder builder = RestClient.builder().baseUrl("http://auth");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final AuthClient client = new AuthClient(builder.build());

    @BeforeEach
    void callerIsAnAdmin() {
        var admin = new AuthenticatedUser(1L, "admin@example.com", Role.ADMIN);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(admin, "jwt-1", List.of()));
    }

    @AfterEach
    void clearCaller() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsTheUserAndForwardsTheCallersToken() {
        server.expect(requestTo(USER_URL))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer jwt-1"))
                .andRespond(withSuccess("""
                        {"id":9,"email":"d@example.com","fullName":"Driver",
                         "role":"DRIVER","phone":"1234567"}""", MediaType.APPLICATION_JSON));

        assertThat(client.findUser(9L)).contains(new AuthClient.UserView(9L, "d@example.com", "Driver", Role.DRIVER, "1234567"));
        server.verify();
    }

    @Test
    void readsTheCallersOwnDetails() {
        server.expect(requestTo("http://auth/api/v1/users/me"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer jwt-1"))
                .andRespond(withSuccess("""
                        {"id":1,"email":"d@example.com","fullName":"Me","role":"DRIVER","phone":null}""", MediaType.APPLICATION_JSON));

        assertThat(client.currentUser().fullName()).isEqualTo("Me");
    }

    @Test
    void aCallerWhoseAccountIsGoneIsRefused() {
        server.expect(requestTo("http://auth/api/v1/users/me")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertFailsWith(client::currentUser, CommonErrorCode.FORBIDDEN);
    }

    @Test
    void missingUserIsEmpty() {
        server.expect(requestTo(USER_URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(client.findUser(9L)).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = HttpStatus.class, names = {"UNAUTHORIZED", "FORBIDDEN"})
    void refusalFromAuthServiceIsForbidden(HttpStatus status) {
        server.expect(requestTo(USER_URL)).andRespond(withStatus(status));

        assertFailsWith(() -> client.findUser(9L), CommonErrorCode.FORBIDDEN);
    }

    @Test
    void serverErrorMeansTheDependencyIsUnavailable() {
        server.expect(requestTo(USER_URL)).andRespond(withServerError());

        assertFailsWith(() -> client.findUser(9L), CommonErrorCode.DEPENDENCY_UNAVAILABLE);
    }

    @Test
    void networkFailureMeansTheDependencyIsUnavailable() {
        server.expect(requestTo(USER_URL)).andRespond(request -> {
            throw new IOException("connection refused");
        });

        assertFailsWith(() -> client.findUser(9L), CommonErrorCode.DEPENDENCY_UNAVAILABLE);
    }

    @Test
    void refusesToCallWithoutACallerToken() {
        SecurityContextHolder.clearContext();

        assertFailsWith(() -> client.findUser(9L), CommonErrorCode.FORBIDDEN);
    }

    private static void assertFailsWith(ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(code));
    }
}
