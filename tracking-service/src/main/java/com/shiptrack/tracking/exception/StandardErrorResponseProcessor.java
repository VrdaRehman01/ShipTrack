package com.shiptrack.tracking.exception;

import com.shiptrack.tracking.config.CorrelationIdFilter;
import io.micronaut.context.annotation.Primary;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.server.exceptions.response.Error;
import io.micronaut.http.server.exceptions.response.ErrorContext;
import io.micronaut.http.server.exceptions.response.ErrorResponseProcessor;
import jakarta.inject.Singleton;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.stream.Collectors;

/**
 * Renders every built-in Micronaut error (validation, 404 route, 401/403, bad JSON, 500) in the
 * standard ShipTrack error shape, so clients handle tracking-service exactly like the Spring services.
 */
@Primary
@Singleton
public class StandardErrorResponseProcessor implements ErrorResponseProcessor<ApiError> {

    @Override
    public MutableHttpResponse<ApiError> processResponse(ErrorContext context, MutableHttpResponse<?> baseResponse) {
        HttpStatus status = baseResponse.status();
        String code = codeFor(status, context);
        String message = status.getCode() >= 500
                ? "Something went wrong. Quote the correlationId when reporting it."
                : messageFor(status, context);
        ApiError body = new ApiError(Instant.now().truncatedTo(ChronoUnit.SECONDS), status.getCode(), code, message,
                context.getRequest().getPath(), CorrelationIdFilter.of(context.getRequest()));
        return baseResponse.body(body);
    }

    static String codeFor(HttpStatus status, ErrorContext context) {
        return switch (status) {
            case BAD_REQUEST -> context.getRootCause().filter(ConstraintViolationException.class::isInstance).isPresent()
                    ? "VALIDATION_FAILED" : "MALFORMED_REQUEST";
            case UNAUTHORIZED -> "UNAUTHORIZED";
            case FORBIDDEN -> "FORBIDDEN";
            case NOT_FOUND -> "NOT_FOUND";
            case METHOD_NOT_ALLOWED -> "METHOD_NOT_ALLOWED";
            case UNSUPPORTED_MEDIA_TYPE -> "MALFORMED_REQUEST";
            case SERVICE_UNAVAILABLE -> "DEPENDENCY_UNAVAILABLE";
            default -> status.getCode() >= 500 ? "INTERNAL_ERROR" : status.name();
        };
    }

    private static String messageFor(HttpStatus status, ErrorContext context) {
        String joined = context.getErrors().stream()
                .map(StandardErrorResponseProcessor::describe)
                .sorted()
                .collect(Collectors.joining("; "));
        if (!joined.isBlank()) {
            return joined;
        }
        return switch (status) {
            case UNAUTHORIZED -> "Missing, invalid or expired token";
            case FORBIDDEN -> "You are not allowed to access this resource";
            case NOT_FOUND -> "No endpoint at " + context.getRequest().getPath();
            default -> status.getReason();
        };
    }

    /** "latitude: must be less than or equal to 90" instead of "postLocation.request.latitude: ...". */
    private static String describe(Error error) {
        String message = error.getMessage();
        int colon = message.indexOf(": ");
        if (colon > 0) {
            String path = message.substring(0, colon);
            String field = path.substring(path.lastIndexOf('.') + 1);
            return field + message.substring(colon);
        }
        return message;
    }
}
