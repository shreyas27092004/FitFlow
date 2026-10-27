package com.fitflow.config;

import com.fitflow.common.filter.CorrelationIdFilter;
import com.fitflow.security.JwtAuthenticationFilter;
import com.fitflow.security.PublicRoutes;
import com.fitflow.security.RestAccessDeniedHandler;
import com.fitflow.security.RestAuthenticationEntryPoint;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final CorrelationIdFilter correlationIdFilter;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;
    private final Environment environment;

    @Value("${fitflow.security.cors.allowed-origins:http://localhost:3000,http://localhost:5173}")
    private String corsAllowedOrigins;

    /**
     * Password hashing baseline: BCrypt with cost factor 12 (Rule R2 / Stage 2).
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        boolean isDev = isProfileActive("dev");
        boolean isTest = isProfileActive("test");

        http
                // CSRF is disabled intentionally: FitFlow is a pure stateless REST API authenticated via
                // Authorization: Bearer JWT headers. Browsers do not automatically attach Bearer tokens,
                // making standard cross-site request forgery impossible.
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .contentTypeOptions(Customizer.withDefaults())
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .cacheControl(Customizer.withDefaults())
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .authorizeHttpRequests(auth -> {
                    // System routes (e.g. error dispatch)
                    auth.requestMatchers("/error").permitAll();

                    // Public POST routes
                    for (String postRoute : PublicRoutes.PUBLIC_POST_ROUTES) {
                        auth.requestMatchers(HttpMethod.POST, postRoute).permitAll();
                    }

                    // Public GET routes
                    for (String getRoute : PublicRoutes.PUBLIC_GET_ROUTES) {
                        auth.requestMatchers(HttpMethod.GET, getRoute).permitAll();
                    }

                    // Swagger / OpenAPI documentation (dev & test only)
                    if (isDev || isTest) {
                        for (String swaggerRoute : PublicRoutes.SWAGGER_ROUTES) {
                            auth.requestMatchers(swaggerRoute).permitAll();
                        }
                    }

                    // Dev tools / GreenMail inspector (dev only)
                    if (isDev) {
                        for (String devRoute : PublicRoutes.DEV_ROUTES) {
                            auth.requestMatchers(devRoute).permitAll();
                        }
                    }

                    // Deny by default: everything else requires authentication
                    auth.anyRequest().authenticated();
                })
                .addFilterBefore(correlationIdFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(authCacheControlFilter(), JwtAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> origins = Arrays.stream(corsAllowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private Filter authCacheControlFilter() {
        return (request, response, chain) -> {
            HttpServletResponse httpServletResponse = (HttpServletResponse) response;
            String uri = ((jakarta.servlet.http.HttpServletRequest) request).getRequestURI();
            if (uri != null && uri.startsWith("/api/v1/auth/")) {
                httpServletResponse.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
                httpServletResponse.setHeader("Pragma", "no-cache");
            }
            chain.doFilter(request, response);
        };
    }

    private boolean isProfileActive(String profile) {
        if (environment == null) {
            return false;
        }
        return Arrays.asList(environment.getActiveProfiles()).contains(profile);
    }
}
