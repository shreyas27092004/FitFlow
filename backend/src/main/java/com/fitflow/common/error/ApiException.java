package com.fitflow.common.error;

import lombok.Getter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import java.util.Collections;
import java.util.Map;

@Getter
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;
    private final HttpStatus status;
    private final HttpHeaders headers;
    private final Map<String, Object> properties;

    public ApiException(ErrorCode errorCode, HttpStatus status, String message) {
        this(errorCode, status, message, new HttpHeaders(), Collections.emptyMap());
    }

    public ApiException(ErrorCode errorCode, HttpStatus status, String message, HttpHeaders headers) {
        this(errorCode, status, message, headers, Collections.emptyMap());
    }

    public ApiException(ErrorCode errorCode, HttpStatus status, String message, HttpHeaders headers, Map<String, Object> properties) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
        this.headers = headers != null ? headers : new HttpHeaders();
        this.properties = properties != null ? properties : Collections.emptyMap();
    }

    public static ApiException notFound(String message) {
        return new ApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, message);
    }

    public static ApiException badRequest(String message) {
        return validationFailed(message);
    }

    public static ApiException conflict(String message) {
        return new ApiException(ErrorCode.CONFLICT, HttpStatus.CONFLICT, message);
    }

    public static ApiException conflict(String message, Map<String, Object> properties) {
        return new ApiException(ErrorCode.CONFLICT, HttpStatus.CONFLICT, message, new HttpHeaders(), properties);
    }

    public static ApiException conflict(String code, String message) {
        ErrorCode ec = ErrorCode.CONFLICT;
        try {
            ec = ErrorCode.valueOf(code);
        } catch (IllegalArgumentException ignored) {
        }
        return new ApiException(ec, HttpStatus.CONFLICT, message);
    }

    public static ApiException conflict(String code, String message, Map<String, Object> properties) {
        ErrorCode ec = ErrorCode.CONFLICT;
        try {
            ec = ErrorCode.valueOf(code);
        } catch (IllegalArgumentException ignored) {
        }
        return new ApiException(ec, HttpStatus.CONFLICT, message, new HttpHeaders(), properties);
    }

    public static ApiException unauthenticated(String message) {
        return new ApiException(ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED, message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, message);
    }

    public static ApiException validationFailed(String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, message);
    }

    public static ApiException ageRestricted(String message) {
        return new ApiException(ErrorCode.AGE_RESTRICTED, HttpStatus.BAD_REQUEST, message);
    }

    public static ApiException ageRestrictedForbidden(String message) {
        return new ApiException(ErrorCode.AGE_RESTRICTED, HttpStatus.FORBIDDEN, message);
    }

    public static ApiException rateLimited(String message) {
        return new ApiException(ErrorCode.RATE_LIMITED, HttpStatus.TOO_MANY_REQUESTS, message);
    }

    public static ApiException locked(String message, long retryAfterSeconds) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        return new ApiException(ErrorCode.LOCKED, HttpStatus.TOO_MANY_REQUESTS, message, headers);
    }

    public static ApiException sessionFull(String message) {
        return new ApiException(ErrorCode.SESSION_FULL, HttpStatus.CONFLICT, message);
    }

    public static ApiException optimisticLock(String message) {
        return new ApiException(ErrorCode.OPTIMISTIC_LOCK, HttpStatus.CONFLICT, message);
    }

    public static ApiException internalError(String message) {
        return new ApiException(ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, message);
    }

    public static ApiException bookingViolation(String message) {
        return new ApiException(ErrorCode.BOOKING_RULE_VIOLATION, HttpStatus.CONFLICT, message);
    }

    public static ApiException bookingRuleViolation(String message) {
        return new ApiException(ErrorCode.BOOKING_RULE_VIOLATION, HttpStatus.CONFLICT, message);
    }

    public static ApiException idempotencyReplayMismatch(String message) {
        return new ApiException(ErrorCode.IDEMPOTENCY_REPLAY_MISMATCH, HttpStatus.UNPROCESSABLE_ENTITY, message);
    }

    public static ApiException upgradeRequired(String message) {
        return new ApiException(ErrorCode.UPGRADE_REQUIRED, HttpStatus.CONFLICT, message);
    }

    public static ApiException upgradeRequired(String message, Object plans) {
        return new ApiException(ErrorCode.UPGRADE_REQUIRED, HttpStatus.CONFLICT, message, new HttpHeaders(), Map.of("plans", plans));
    }
}
