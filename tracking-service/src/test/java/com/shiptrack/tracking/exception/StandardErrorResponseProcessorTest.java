package com.shiptrack.tracking.exception;

import static org.assertj.core.api.Assertions.assertThat;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.server.exceptions.response.ErrorContext;
import jakarta.validation.ConstraintViolationException;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class StandardErrorResponseProcessorTest {

    private final StandardErrorResponseProcessor processor = new StandardErrorResponseProcessor();
    private final HttpRequest<?> request = HttpRequest.GET("/api/v1/tracking/1/latest");

    private ApiError process(HttpStatus status, ErrorContext context) {
        return processor.processResponse(context, HttpResponse.status(status)).body();
    }

    private ErrorContext noDetails() {
        return ErrorContext.builder(request).build();
    }

    @ParameterizedTest
    @CsvSource({
        "UNAUTHORIZED, UNAUTHORIZED",
        "FORBIDDEN, FORBIDDEN",
        "NOT_FOUND, NOT_FOUND",
        "METHOD_NOT_ALLOWED, METHOD_NOT_ALLOWED",
        "UNSUPPORTED_MEDIA_TYPE, MALFORMED_REQUEST",
        "SERVICE_UNAVAILABLE, DEPENDENCY_UNAVAILABLE",
        "INTERNAL_SERVER_ERROR, INTERNAL_ERROR",
        "BAD_GATEWAY, INTERNAL_ERROR",
        "CONFLICT, CONFLICT",
        "BAD_REQUEST, MALFORMED_REQUEST",
    })
    void everyStatusGetsOurErrorCode(HttpStatus status, String code) {
        assertThat(StandardErrorResponseProcessor.codeFor(status, noDetails())).isEqualTo(code);
    }

    @Test
    void aFailedValidationIsNamedAsSuch() {
        ErrorContext context = ErrorContext.builder(request).cause(new ConstraintViolationException("bad", Set.of())).build();

        assertThat(StandardErrorResponseProcessor.codeFor(HttpStatus.BAD_REQUEST, context)).isEqualTo("VALIDATION_FAILED");
    }

    @ParameterizedTest
    @CsvSource({
        "UNAUTHORIZED, 'Missing, invalid or expired token'",
        "FORBIDDEN, You are not allowed to access this resource",
        "NOT_FOUND, No endpoint at /api/v1/tracking/1/latest",
        "CONFLICT, Conflict",
    })
    void givesAPlainMessageWhenThereAreNoDetails(HttpStatus status, String message) {
        assertThat(process(status, noDetails()).message()).isEqualTo(message);
    }

    @Test
    void shortensValidationPathsToTheFieldName() {
        ErrorContext context = ErrorContext.builder(request).errorMessage("post.body.latitude: must be at most 90").build();

        assertThat(process(HttpStatus.BAD_REQUEST, context).message()).isEqualTo("latitude: must be at most 90");
    }

    @Test
    void neverLeaksTheCauseOfAServerError() {
        ErrorContext context = ErrorContext.builder(request).errorMessage("SQL syntax error near users").build();

        ApiError body = process(HttpStatus.INTERNAL_SERVER_ERROR, context);

        assertThat(body.message()).doesNotContain("SQL");
        assertThat(body.status()).isEqualTo(500);
        assertThat(body.path()).isEqualTo("/api/v1/tracking/1/latest");
    }
}
