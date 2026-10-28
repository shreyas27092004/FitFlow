package com.fitflow.wellness;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.MethodParameter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPattern;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class AdminWellnessIsolationTest {

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    private static final Set<String> FORBIDDEN_WELLNESS_TYPE_NAMES = Set.of(
            "FitnessProfile",
            "MemberHealthProfile",
            "BodyMetric",
            "BodyMetricResponse",
            "ScreeningResult",
            "SafetyVerdict",
            "HealthConsent",
            "ClassFeedback",
            "RestrictionProposal",
            "TrainerPrepResponse"
    );

    @Test
    @DisplayName("Admin Isolation: None of the /api/v1/admin routes returns wellness data or touches health models")
    void testNoAdminRouteReturnsWellnessData() {
        Map<RequestMappingInfo, HandlerMethod> handlerMethods = handlerMapping.getHandlerMethods();
        int adminRoutesChecked = 0;

        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMethods.entrySet()) {
            RequestMappingInfo mappingInfo = entry.getKey();
            HandlerMethod handlerMethod = entry.getValue();

            Set<String> patterns = Collections.emptySet();
            if (mappingInfo.getPathPatternsCondition() != null) {
                patterns = mappingInfo.getPathPatternsCondition().getPatterns().stream()
                        .map(PathPattern::getPatternString)
                        .collect(Collectors.toSet());
            } else if (mappingInfo.getPatternsCondition() != null) {
                patterns = mappingInfo.getPatternsCondition().getPatterns();
            }

            for (String pattern : patterns) {
                if (pattern.startsWith("/api/v1/admin")) {
                    adminRoutesChecked++;

                    // 1. Controller must NOT reside in com.fitflow.wellness
                    String packageName = handlerMethod.getBeanType().getPackageName();
                    assertFalse(packageName.contains(".wellness"),
                            "Admin route " + pattern + " must not be handled by wellness package: " + handlerMethod.getBeanType().getName());

                    // 2. Return type must not be a wellness model
                    Class<?> returnType = handlerMethod.getReturnType().getParameterType();
                    for (String forbidden : FORBIDDEN_WELLNESS_TYPE_NAMES) {
                        assertFalse(returnType.getSimpleName().contains(forbidden),
                                "Admin route " + pattern + " returns forbidden wellness type: " + returnType.getName());
                    }

                    // 3. Controller dependencies must not include health repositories or HealthDataCodec
                    for (Field field : handlerMethod.getBeanType().getDeclaredFields()) {
                        String typeName = field.getType().getSimpleName();
                        assertFalse(typeName.contains("MemberHealthProfile"),
                                "Admin controller " + handlerMethod.getBeanType().getSimpleName() + " must not inject health repository");
                        assertFalse(typeName.contains("HealthDataCodec"),
                                "Admin controller " + handlerMethod.getBeanType().getSimpleName() + " must not inject HealthDataCodec");
                        assertFalse(typeName.contains("BodyMetricRepository"),
                                "Admin controller " + handlerMethod.getBeanType().getSimpleName() + " must not inject BodyMetricRepository");
                    }
                }
            }
        }

        assertTrue(adminRoutesChecked > 0, "At least one /api/v1/admin route should be verified");
    }
}
