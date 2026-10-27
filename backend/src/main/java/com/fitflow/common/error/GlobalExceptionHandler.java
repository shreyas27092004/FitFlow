package com.fitflow.common.error;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String MDC_TRACE_ID = "traceId";
    private static final String PROBLEM_JSON_MEDIA_TYPE = "application/problem+json";

    public record ValidationErrorItem(String field, String message) {}

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        String traceId = resolveTraceId();
        String uri = resolveRequestUri(request);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Request validation failed"
        );
        problem.setType(URI.create("urn:problem-type:validation-failed"));
        problem.setTitle("Validation Failed");
        problem.setInstance(URI.create(uri));
        problem.setProperty("code", ErrorCode.VALIDATION_FAILED.name());
        problem.setProperty("traceId", traceId);

        List<ValidationErrorItem> errors = new ArrayList<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            errors.add(new ValidationErrorItem(fe.getField(), fe.getDefaultMessage()));
        }
        problem.setProperty("errors", errors);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.parseMediaType(PROBLEM_JSON_MEDIA_TYPE))
                .body(problem);
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> handleApiException(ApiException ex, HttpServletRequest request) {
        String traceId = resolveTraceId();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
        problem.setType(URI.create("urn:problem-type:" + ex.getErrorCode().name().toLowerCase().replace('_', '-')));
        problem.setTitle(toTitle(ex.getErrorCode()));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", ex.getErrorCode().name());
        problem.setProperty("traceId", traceId);

        var builder = ResponseEntity
                .status(ex.getStatus())
                .contentType(MediaType.parseMediaType(PROBLEM_JSON_MEDIA_TYPE));
        if (ex.getHeaders() != null && !ex.getHeaders().isEmpty()) {
            builder.headers(ex.getHeaders());
        }

        return builder.body(problem);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handleOptimisticLock(OptimisticLockingFailureException ex, HttpServletRequest request) {
        String traceId = resolveTraceId();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                "The resource was modified by another concurrent transaction. Please retry."
        );
        problem.setType(URI.create("urn:problem-type:optimistic-lock"));
        problem.setTitle("Concurrent Modification");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", ErrorCode.OPTIMISTIC_LOCK.name());
        problem.setProperty("traceId", traceId);

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .contentType(MediaType.parseMediaType(PROBLEM_JSON_MEDIA_TYPE))
                .body(problem);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthenticationException(AuthenticationException ex, HttpServletRequest request) {
        String traceId = resolveTraceId();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED,
                "Full authentication is required to access this resource."
        );
        problem.setType(URI.create("urn:problem-type:unauthenticated"));
        problem.setTitle("Unauthenticated");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", ErrorCode.UNAUTHENTICATED.name());
        problem.setProperty("traceId", traceId);

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.parseMediaType(PROBLEM_JSON_MEDIA_TYPE))
                .body(problem);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        String traceId = resolveTraceId();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN,
                "You do not have permission to perform this action."
        );
        problem.setType(URI.create("urn:problem-type:forbidden"));
        problem.setTitle("Forbidden");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", ErrorCode.FORBIDDEN.name());
        problem.setProperty("traceId", traceId);

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .contentType(MediaType.parseMediaType(PROBLEM_JSON_MEDIA_TYPE))
                .body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGenericException(Exception ex, HttpServletRequest request) {
        String traceId = resolveTraceId();
        log.error("Unhandled exception captured with traceId={}: {}", traceId, ex.getMessage(), ex);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected internal error occurred. Please contact support referencing the trace ID."
        );
        problem.setType(URI.create("urn:problem-type:internal-error"));
        problem.setTitle("Internal Server Error");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", ErrorCode.INTERNAL_ERROR.name());
        problem.setProperty("traceId", traceId);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.parseMediaType(PROBLEM_JSON_MEDIA_TYPE))
                .body(problem);
    }

    private String resolveTraceId() {
        String traceId = MDC.get(MDC_TRACE_ID);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }
        return traceId;
    }

    private String resolveRequestUri(WebRequest request) {
        if (request instanceof ServletWebRequest swr) {
            return swr.getRequest().getRequestURI();
        }
        return "/";
    }

    private String toTitle(ErrorCode code) {
        String name = code.name().replace('_', ' ').toLowerCase();
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
