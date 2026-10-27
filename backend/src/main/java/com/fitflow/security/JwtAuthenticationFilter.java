package com.fitflow.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.common.error.ApiException;
import com.fitflow.common.error.ErrorCode;
import com.fitflow.user.UserStatusService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final TokenService tokenService;
    private final UserStatusService userStatusService;
    private final ObjectMapper objectMapper;
    private final Environment environment;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length()).trim();

        try {
            UserPrincipal principal = tokenService.parseTokenToPrincipal(token);

            if (!userStatusService.isActive(principal.getId())) {
                sendProblemResponse(response, request, HttpStatus.UNAUTHORIZED,
                        ErrorCode.UNAUTHENTICATED, "User account is suspended or no longer active.");
                return;
            }

            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    principal.getAuthorities()
            );
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);

            filterChain.doFilter(request, response);
        } catch (ApiException e) {
            log.debug("JWT authentication failed: {}", e.getMessage());
            // Check if route is public
            boolean isDev = isProfileActive("dev");
            boolean isTest = isProfileActive("test");
            if (PublicRoutes.isPublic(request.getMethod(), request.getRequestURI(), isDev, isTest)) {
                // If route is public, ignore bad token and allow through unauthenticated
                filterChain.doFilter(request, response);
            } else {
                sendProblemResponse(response, request, e.getStatus(), e.getErrorCode(), e.getMessage());
            }
        }
    }

    private void sendProblemResponse(
            HttpServletResponse response,
            HttpServletRequest request,
            HttpStatus status,
            ErrorCode errorCode,
            String detail) throws IOException {

        String traceId = MDC.get("traceId");
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("urn:problem-type:" + errorCode.name().toLowerCase().replace('_', '-')));
        problem.setTitle("Authentication Failed");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", errorCode.name());
        problem.setProperty("traceId", traceId);

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }

    private boolean isProfileActive(String profile) {
        if (environment == null) {
            return false;
        }
        return Arrays.asList(environment.getActiveProfiles()).contains(profile);
    }
}
