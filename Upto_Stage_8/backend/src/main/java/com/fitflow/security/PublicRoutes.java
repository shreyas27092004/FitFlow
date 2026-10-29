package com.fitflow.security;

import org.springframework.util.AntPathMatcher;

import java.util.List;

public final class PublicRoutes {

    private PublicRoutes() {}

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    public static final List<String> PUBLIC_POST_ROUTES = List.of(
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/forgot-password",
            "/api/v1/auth/reset-password",
            "/api/v1/payments/webhooks/razorpay"
    );

    public static final List<String> PUBLIC_GET_ROUTES = List.of(
            "/actuator/health",
            "/api/v1/plans"
    );

    public static final List<String> SWAGGER_ROUTES = List.of(
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            "/swagger-resources/**"
    );

    public static final List<String> DEV_ROUTES = List.of(
            "/dev/mail",
            "/dev/razorpay-checkout"
    );

    public static final List<String> SYSTEM_ROUTES = List.of(
            "/error"
    );

    public static boolean isPublic(String httpMethod, String path, boolean isDev, boolean isTest) {
        for (String route : SYSTEM_ROUTES) {
            if (PATH_MATCHER.match(route, path)) {
                return true;
            }
        }
        if ("POST".equalsIgnoreCase(httpMethod)) {
            for (String route : PUBLIC_POST_ROUTES) {
                if (PATH_MATCHER.match(route, path)) {
                    return true;
                }
            }
        }

        if ("GET".equalsIgnoreCase(httpMethod)) {
            for (String route : PUBLIC_GET_ROUTES) {
                if (PATH_MATCHER.match(route, path)) {
                    return true;
                }
            }

            if (isDev || isTest) {
                for (String route : SWAGGER_ROUTES) {
                    if (PATH_MATCHER.match(route, path)) {
                        return true;
                    }
                }
            }

            if (isDev || isTest) {
                for (String route : DEV_ROUTES) {
                    if (PATH_MATCHER.match(route, path)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }
}
