package com.shiptrack.common.error;

import com.shiptrack.common.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Turns every exception into the standard ShipTrack error shape. Security errors raised in the
 * filter chain are routed here too, so 401/403 look the same as everything else.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApi(ApiException ex, HttpServletRequest request) {
        if (ex.getCode().status().is5xxServerError()) {
            log.warn("{} on {} {}: {}", ex.getCode().name(), request.getMethod(), request.getRequestURI(), ex.getMessage());
        }
        return build(ex.getCode(), ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .sorted()
                .collect(Collectors.joining("; "));
        return build(CommonErrorCode.VALIDATION_FAILED, message, request);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiError> handleMethodValidation(HandlerMethodValidationException ex, HttpServletRequest request) {
        // A @Valid body inside method validation arrives as ParameterErrors: report its fields, not "request".
        String message = ex.getParameterValidationResults().stream()
                .flatMap(r -> r instanceof ParameterErrors errors
                        ? errors.getFieldErrors().stream().map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                        : r.getResolvableErrors().stream()
                                .map(e -> parameterName(r) + ": " + e.getDefaultMessage()))
                .sorted()
                .collect(Collectors.joining("; "));
        return build(CommonErrorCode.VALIDATION_FAILED, message, request);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, HttpMediaTypeNotSupportedException.class})
    public ResponseEntity<ApiError> handleUnreadable(Exception ex, HttpServletRequest request) {
        return build(CommonErrorCode.MALFORMED_REQUEST, "Request body is missing or not valid JSON", request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return build(CommonErrorCode.MALFORMED_REQUEST, "Invalid value for parameter '" + ex.getName() + "'", request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParam(MissingServletRequestParameterException ex, HttpServletRequest request) {
        return build(CommonErrorCode.MALFORMED_REQUEST, "Missing parameter '" + ex.getParameterName() + "'", request);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiError> handleMissingHeader(MissingRequestHeaderException ex, HttpServletRequest request) {
        return build(CommonErrorCode.MALFORMED_REQUEST, "Missing header '" + ex.getHeaderName() + "'", request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        return build(CommonErrorCode.UNAUTHORIZED, "Missing, invalid or expired token", request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return build(CommonErrorCode.FORBIDDEN, "You are not allowed to access this resource", request);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleOptimisticLock(OptimisticLockingFailureException ex, HttpServletRequest request) {
        return build(CommonErrorCode.CONCURRENT_MODIFICATION,
                "Someone else changed this resource at the same time. Reload it and try again.", request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Data integrity violation on {} {}", request.getMethod(), request.getRequestURI());
        return build(CommonErrorCode.CONFLICT, "The request conflicts with existing data", request);
    }

    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<ApiError> handleDownstream(ResourceAccessException ex, HttpServletRequest request) {
        log.warn("Downstream call failed on {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return build(CommonErrorCode.DEPENDENCY_UNAVAILABLE,
                "A dependent service is unavailable. Please try again shortly.", request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
        return build(CommonErrorCode.NOT_FOUND, "No endpoint at " + request.getRequestURI(), request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethod(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return build(CommonErrorCode.METHOD_NOT_ALLOWED, ex.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error on {} {}", request.getMethod(), request.getRequestURI(), ex);
        // Never leak internals (stack traces, SQL) to the client.
        return build(CommonErrorCode.INTERNAL_ERROR,
                "Something went wrong. Quote the correlationId when reporting it.", request);
    }

    /** Prefer the HTTP-facing name (header/query param) over the Java parameter name. */
    private static String parameterName(ParameterValidationResult result) {
        MethodParameter parameter = result.getMethodParameter();
        RequestHeader header = parameter.getParameterAnnotation(RequestHeader.class);
        if (header != null && !header.name().isEmpty()) {
            return header.name();
        }
        RequestParam param = parameter.getParameterAnnotation(RequestParam.class);
        if (param != null && !param.name().isEmpty()) {
            return param.name();
        }
        return parameter.getParameterName();
    }

    private ResponseEntity<ApiError> build(ErrorCode code, String message, HttpServletRequest request) {
        ApiError body = new ApiError(
                Instant.now().truncatedTo(ChronoUnit.SECONDS),
                code.status().value(),
                code.name(),
                message,
                request.getRequestURI(),
                MDC.get(CorrelationIdFilter.MDC_KEY));
        return ResponseEntity.status(code.status()).body(body);
    }
}
