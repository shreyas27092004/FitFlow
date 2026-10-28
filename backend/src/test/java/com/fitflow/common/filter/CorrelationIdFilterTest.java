package com.fitflow.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    @DisplayName("Filter preserves provided X-Request-Id, injects into MDC, and sets in response header")
    void testPreservesProvidedRequestId() throws ServletException, IOException {
        String existingId = "client-req-uuid-98765";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER_REQUEST_ID, existingId);
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> capturedMdc = new AtomicReference<>();
        FilterChain chain = (req, res) -> capturedMdc.set(MDC.get(CorrelationIdFilter.MDC_KEY_TRACE_ID));

        filter.doFilter(request, response, chain);

        assertEquals(existingId, capturedMdc.get(), "MDC must hold the incoming correlation id during request");
        assertEquals(existingId, response.getHeader(CorrelationIdFilter.HEADER_REQUEST_ID), "Response header must return correlation id");
        assertNull(MDC.get(CorrelationIdFilter.MDC_KEY_TRACE_ID), "MDC must be cleared after request completes");
    }

    @Test
    @DisplayName("Filter generates UUID when X-Request-Id header is absent")
    void testGeneratesUuidWhenHeaderAbsent() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> capturedMdc = new AtomicReference<>();
        FilterChain chain = (req, res) -> capturedMdc.set(MDC.get(CorrelationIdFilter.MDC_KEY_TRACE_ID));

        filter.doFilter(request, response, chain);

        String generated = capturedMdc.get();
        assertNotNull(generated, "MDC must hold generated correlation id");
        assertNotNull(UUID.fromString(generated), "Generated correlation id must be a valid UUID");
        assertEquals(generated, response.getHeader(CorrelationIdFilter.HEADER_REQUEST_ID));
        assertNull(MDC.get(CorrelationIdFilter.MDC_KEY_TRACE_ID), "MDC must be cleared after request");
    }
}
