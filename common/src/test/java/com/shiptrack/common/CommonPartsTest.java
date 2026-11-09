package com.shiptrack.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shiptrack.common.error.ApiError;
import com.shiptrack.common.error.ApiException;
import com.shiptrack.common.error.CommonErrorCode;
import com.shiptrack.common.error.GlobalExceptionHandler;
import com.shiptrack.common.security.AuthenticatedUser;
import com.shiptrack.common.security.CurrentUser;
import com.shiptrack.common.security.JwtProperties;
import com.shiptrack.common.security.JwtVerifier;
import com.shiptrack.common.security.Role;
import com.shiptrack.common.web.CorrelationIdFilter;
import com.shiptrack.common.web.CorrelationIdInterceptor;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.MissingRequestHeaderException;

class CommonPartsTest {

    private static final String SECRET = "common-parts-secret-at-least-32-characters";
    private static final Instant NOW = Instant.parse("2026-11-04T09:30:00Z");

    @AfterEach
    void cleanUp() {
        MDC.clear();
        SecurityContextHolder.clearContext();
    }

    // ------------------------------------------------------------ outgoing correlation id

    private HttpHeaders interceptWith(HttpHeaders headers) throws IOException {
        HttpRequest request = mock(HttpRequest.class);
        ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);
        when(request.getHeaders()).thenReturn(headers);
        new CorrelationIdInterceptor().intercept(request, new byte[0], execution);
        verify(execution).execute(any(), any());
        return headers;
    }

    @Test
    void interceptorPassesTheCurrentIdToTheNextService() throws IOException {
        MDC.put(CorrelationIdFilter.MDC_KEY, "abc");
        assertThat(interceptWith(new HttpHeaders()).getFirst(CorrelationIdFilter.HEADER)).isEqualTo("abc");
    }

    @Test
    void interceptorAddsNothingOutsideARequest() throws IOException {
        assertThat(interceptWith(new HttpHeaders()).containsHeader(CorrelationIdFilter.HEADER)).isFalse();
    }

    @Test
    void interceptorKeepsAnIdAlreadySet() throws IOException {
        MDC.put(CorrelationIdFilter.MDC_KEY, "abc");
        HttpHeaders headers = new HttpHeaders();
        headers.set(CorrelationIdFilter.HEADER, "existing");
        assertThat(interceptWith(headers).getFirst(CorrelationIdFilter.HEADER)).isEqualTo("existing");
    }

    // ------------------------------------------------------------ incoming correlation id

    private HttpServletRequest filtered(MockHttpServletRequest request) throws Exception {
        MockFilterChain chain = new MockFilterChain();
        new CorrelationIdFilter().doFilter(request, new MockHttpServletResponse(), chain);
        return (HttpServletRequest) chain.getRequest();
    }

    @Test
    void generatedIdIsVisibleAsAHeaderDownstream() throws Exception {
        MockHttpServletRequest original = new MockHttpServletRequest();
        original.addHeader("Accept", "application/json");

        HttpServletRequest seen = filtered(original);

        List<String> names = Collections.list(seen.getHeaderNames());
        assertThat(names).contains("Accept", CorrelationIdFilter.HEADER);
        String id = seen.getHeader(CorrelationIdFilter.HEADER);
        assertThat(Collections.list(seen.getHeaders(CorrelationIdFilter.HEADER))).containsExactly(id);
        assertThat(Collections.list(seen.getHeaders("Accept"))).containsExactly("application/json");
    }

    @Test
    void aSuppliedIdIsNotListedTwice() throws Exception {
        MockHttpServletRequest original = new MockHttpServletRequest();
        original.addHeader(CorrelationIdFilter.HEADER, "abc-123");

        HttpServletRequest seen = filtered(original);

        assertThat(seen.getHeader(CorrelationIdFilter.HEADER)).isEqualTo("abc-123");
        assertThat(Collections.list(seen.getHeaderNames())).containsOnlyOnce(CorrelationIdFilter.HEADER);
    }

    @Test
    void anIdThatCouldPolluteTheLogsIsReplaced() throws Exception {
        MockHttpServletRequest original = new MockHttpServletRequest();
        original.addHeader(CorrelationIdFilter.HEADER, "bad id\nFAKE LOG LINE");

        assertThat(filtered(original).getHeader(CorrelationIdFilter.HEADER)).doesNotContain("FAKE");
    }

    // ------------------------------------------------------------ errors

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/x");

    @Test
    void aServerSideApiErrorKeepsItsStatus() {
        var response = handler.handleApi(ApiException.dependencyUnavailable("billing", new IOException("down")), request);

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        ApiError body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.error()).isEqualTo("DEPENDENCY_UNAVAILABLE");
        assertThat(body.message()).doesNotContain("down");
    }

    @Test
    void aMissingHeaderIsAClientError() {
        MissingRequestHeaderException missing = mock(MissingRequestHeaderException.class);
        when(missing.getHeaderName()).thenReturn("X-Thing");

        var response = handler.handleMissingHeader(missing, request);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).contains("X-Thing");
    }

    @Test
    void apiExceptionHelpersUseTheRightCodes() {
        assertThat(ApiException.notFound("User", 4).getCode()).isEqualTo(CommonErrorCode.NOT_FOUND);
        assertThat(ApiException.notFound("User", 4)).hasMessage("User not found: 4");
        assertThat(ApiException.forbidden("no").getCode()).isEqualTo(CommonErrorCode.FORBIDDEN);
        assertThat(new ApiException(CommonErrorCode.CONFLICT, "x", new IOException("cause"))).hasCauseInstanceOf(IOException.class);
    }

    // ------------------------------------------------------------ who is calling

    @Test
    void roleHelpersMatchTheRole() {
        assertThat(new AuthenticatedUser(1L, "a@x.com", Role.ADMIN).isAdmin()).isTrue();
        assertThat(new AuthenticatedUser(1L, "a@x.com", Role.CUSTOMER).isAdmin()).isFalse();
        assertThat(new AuthenticatedUser(1L, "a@x.com", Role.CUSTOMER).isCustomer()).isTrue();
        assertThat(new AuthenticatedUser(1L, "a@x.com", Role.DRIVER).isCustomer()).isFalse();
    }

    @Test
    void thereIsNoTokenToForwardOutsideARequest() {
        assertThat(CurrentUser.bearerToken()).isEmpty();
    }

    @Test
    void anInternalCallHasNoUserTokenToForward() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("internal-service", null, List.of()));
        assertThat(CurrentUser.bearerToken()).isEmpty();
    }

    // ------------------------------------------------------------ session start

    private final JwtVerifier verifier = new JwtVerifier(
            new JwtProperties(SECRET, Duration.ofMinutes(15), "shiptrack-auth", Duration.ofHours(8)), Clock.fixed(NOW, ZoneOffset.UTC));

    private JwtBuilder token() {
        return Jwts.builder().subject("1").issuer("shiptrack-auth").expiration(Date.from(NOW.plusSeconds(60)))
                .signWith(JwtVerifier.signingKey(SECRET), Jwts.SIG.HS256);
    }

    @Test
    void readsWhenTheSessionStarted() {
        String token = token().claim(JwtVerifier.CLAIM_SESSION_START, NOW.getEpochSecond()).compact();
        assertThat(verifier.sessionStart(token)).contains(NOW);
    }

    @Test
    void anOlderTokenHasNoSessionStart() {
        assertThat(verifier.sessionStart(token().compact())).isEmpty();
    }
}
