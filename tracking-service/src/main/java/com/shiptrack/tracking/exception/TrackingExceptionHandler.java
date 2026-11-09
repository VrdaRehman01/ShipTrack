package com.shiptrack.tracking.exception;

import com.shiptrack.tracking.config.CorrelationIdFilter;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import jakarta.inject.Singleton;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Turns our business exceptions into the standard error shape with their own code. */
@Produces
@Singleton
@Requires(classes = {TrackingException.class, ExceptionHandler.class})
public class TrackingExceptionHandler implements ExceptionHandler<TrackingException, HttpResponse<ApiError>> {

    private static final Logger log = LoggerFactory.getLogger(TrackingExceptionHandler.class);

    @Override
    @SuppressWarnings("rawtypes") // Micronaut's ExceptionHandler interface declares this parameter raw
    public HttpResponse<ApiError> handle(HttpRequest request, TrackingException e) {
        if (e.getStatus().getCode() >= 500) {
            // Include the underlying cause (e.g. timeout, connection refused, upstream status) so outages are diagnosable.
            Throwable cause = e.getCause();
            log.warn("{} on {} {}: {} (cause: {})", e.getCode(), request.getMethod(), request.getPath(), e.getMessage(),
                    cause == null ? "none" : cause.getClass().getSimpleName() + ": " + cause.getMessage());
        }
        ApiError body = new ApiError(Instant.now().truncatedTo(ChronoUnit.SECONDS), e.getStatus().getCode(), e.getCode(),
                e.getMessage(), request.getPath(), CorrelationIdFilter.of(request));
        return HttpResponse.<ApiError>status(e.getStatus()).body(body);
    }
}
