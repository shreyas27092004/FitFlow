package com.fitflow.common.error;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.ServletWebRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProblemDetailTest {

    private GlobalExceptionHandler exceptionHandler;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
        MDC.put("traceId", "test-trace-id-12345");
    }

    @Test
    @DisplayName("Validation failure produces 400 ProblemDetail with code=VALIDATION_FAILED and errors array")
    void test400ValidationFailed() throws NoSuchMethodException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/fitness-classes");
        ServletWebRequest webRequest = new ServletWebRequest(request);

        record DummyDto(String name) {}
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new DummyDto(""), "dummyDto");
        bindingResult.addError(new FieldError("dummyDto", "name", "must not be blank"));

        var method = getClass().getDeclaredMethod("test400ValidationFailed");
        var parameter = new org.springframework.core.MethodParameter(method, -1);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, bindingResult);

        ResponseEntity<Object> response = exceptionHandler.handleMethodArgumentNotValid(
                ex,
                new org.springframework.http.HttpHeaders(),
                HttpStatus.BAD_REQUEST,
                webRequest
        );

        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody() instanceof ProblemDetail);

        ProblemDetail pd = (ProblemDetail) response.getBody();
        assertEquals(400, pd.getStatus());
        assertEquals("VALIDATION_FAILED", pd.getProperties().get("code"));
        assertEquals("test-trace-id-12345", pd.getProperties().get("traceId"));

        @SuppressWarnings("unchecked")
        List<GlobalExceptionHandler.ValidationErrorItem> errors =
                (List<GlobalExceptionHandler.ValidationErrorItem>) pd.getProperties().get("errors");
        assertNotNull(errors);
        assertEquals(1, errors.size());
        assertEquals("name", errors.get(0).field());
        assertEquals("must not be blank", errors.get(0).message());
    }

    @Test
    @DisplayName("NotFound ApiException produces 404 ProblemDetail with code=NOT_FOUND")
    void test404NotFound() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/sessions/999");
        ApiException ex = ApiException.notFound("Session with id 999 not found");

        ResponseEntity<ProblemDetail> response = exceptionHandler.handleApiException(ex, request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        ProblemDetail pd = response.getBody();
        assertNotNull(pd);
        assertEquals(404, pd.getStatus());
        assertEquals("NOT_FOUND", pd.getProperties().get("code"));
        assertEquals("Session with id 999 not found", pd.getDetail());
        assertEquals("test-trace-id-12345", pd.getProperties().get("traceId"));
    }

    @Test
    @DisplayName("Unhandled Exception produces 500 ProblemDetail with code=INTERNAL_ERROR, hiding internal message and stack trace")
    void test500InternalErrorHidesDetails() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/payments");
        String secretSqlMessage = "SQLSyntaxErrorException: Table 'fitflow.users' corrupted at line 42";
        RuntimeException internalError = new RuntimeException(secretSqlMessage);

        ResponseEntity<ProblemDetail> response = exceptionHandler.handleGenericException(internalError, request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        ProblemDetail pd = response.getBody();
        assertNotNull(pd);
        assertEquals(500, pd.getStatus());
        assertEquals("INTERNAL_ERROR", pd.getProperties().get("code"));
        assertEquals("test-trace-id-12345", pd.getProperties().get("traceId"));

        // Critical rule: Never expose internal error message or stack trace
        assertFalse(pd.getDetail().contains(secretSqlMessage),
                "Internal error message or stack trace must never be leaked to client!");
        assertTrue(pd.getDetail().contains("An unexpected internal error occurred"),
                "Generic friendly message must be returned");
    }
}
