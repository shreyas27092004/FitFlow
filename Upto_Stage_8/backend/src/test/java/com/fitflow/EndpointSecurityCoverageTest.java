package com.fitflow;

import com.fitflow.security.PublicRoutes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.env.Environment;
import org.springframework.http.server.PathContainer;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPattern;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
public class EndpointSecurityCoverageTest {

    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Autowired
    private Environment environment;

    @Test
    @DisplayName("Security Gate: Every endpoint mapping must be explicitly listed in PublicRoutes or protected with @PreAuthorize")
    void testAllEndpointsAreProtectedOrPublic() {
        boolean isDev = isProfileActive("dev");
        boolean isTest = isProfileActive("test");

        List<String> unprotectedEndpoints = new ArrayList<>();

        Map<RequestMappingInfo, HandlerMethod> handlerMethods = handlerMapping.getHandlerMethods();

        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMethods.entrySet()) {
            RequestMappingInfo mappingInfo = entry.getKey();
            HandlerMethod handlerMethod = entry.getValue();

            // 1. Check if method or class has @PreAuthorize
            boolean isProtected = handlerMethod.hasMethodAnnotation(PreAuthorize.class)
                    || AnnotatedElementUtils.hasAnnotation(handlerMethod.getBeanType(), PreAuthorize.class);

            if (isProtected) {
                continue;
            }

            // 2. Extract path patterns
            Set<String> patterns = Collections.emptySet();
            if (mappingInfo.getPathPatternsCondition() != null) {
                patterns = mappingInfo.getPathPatternsCondition().getPatterns().stream()
                        .map(PathPattern::getPatternString)
                        .collect(Collectors.toSet());
            } else if (mappingInfo.getPatternsCondition() != null) {
                patterns = mappingInfo.getPatternsCondition().getPatterns();
            }

            Set<RequestMethod> methods = mappingInfo.getMethodsCondition().getMethods();
            if (methods.isEmpty()) {
                // Wildcard or default methods: test GET and POST
                methods = Set.of(RequestMethod.GET, RequestMethod.POST);
            }

            for (String path : patterns) {
                for (RequestMethod method : methods) {
                    boolean isPublic = PublicRoutes.isPublic(method.name(), path, isDev, isTest);
                    if (!isPublic) {
                        unprotectedEndpoints.add(String.format(
                                "%s %s (handled by %s#%s)",
                                method.name(),
                                path,
                                handlerMethod.getBeanType().getSimpleName(),
                                handlerMethod.getMethod().getName()
                        ));
                    }
                }
            }
        }

        assertTrue(
                unprotectedEndpoints.isEmpty(),
                "SECURITY GATE FAILURE: Unprotected endpoints found without @PreAuthorize or PublicRoutes entry:\n"
                        + String.join("\n", unprotectedEndpoints)
        );
    }

    private boolean isProfileActive(String profile) {
        if (environment == null) {
            return false;
        }
        return Arrays.asList(environment.getActiveProfiles()).contains(profile);
    }
}
