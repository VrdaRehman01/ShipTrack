package com.shiptrack.common.error;

/** Base for expected business errors; the global handler turns these into the standard error shape. */
public class ApiException extends RuntimeException {

    private final transient ErrorCode code;

    public ApiException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ApiException(ErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public ErrorCode getCode() {
        return code;
    }

    public static ApiException notFound(String resource, Object id) {
        return new ApiException(CommonErrorCode.NOT_FOUND, resource + " not found: " + id);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(CommonErrorCode.FORBIDDEN, message);
    }

    public static ApiException dependencyUnavailable(String service, Throwable cause) {
        return new ApiException(CommonErrorCode.DEPENDENCY_UNAVAILABLE,
                service + " is unavailable right now. Please try again shortly.", cause);
    }
}
