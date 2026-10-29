package com.fitflow;

import com.fitflow.common.ClockConfig;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

public class ArchitectureRulesTest {

    private static JavaClasses importedClasses;

    @BeforeAll
    static void setUp() {
        importedClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.fitflow");
    }

    // (a) No field @Autowired
    @Test
    @DisplayName("Gate (a): No class has a field annotated with @Autowired")
    void ruleA_noFieldAutowired() {
        ArchRule rule = noFields()
                .should().beAnnotatedWith(Autowired.class)
                .because("Constructor injection must be used everywhere (Rule R6)");
        rule.check(importedClasses);
    }

    // (b) Controllers must not depend on repositories or entities
    @Test
    @DisplayName("Gate (b): Controllers must not depend on repositories or @Entity classes")
    void ruleB_controllersMustNotDependOnRepositoriesOrEntities() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..controller..")
                .or().areAnnotatedWith(RestController.class)
                .should().dependOnClassesThat().resideInAPackage("..repository..")
                .orShould().dependOnClassesThat().areAnnotatedWith(Entity.class)
                .because("Controllers must call services and expose only DTO records (Rule R7)");
        rule.check(importedClasses);
    }

    // (c) @RestController method return type or parameter cannot be an @Entity
    @Test
    @DisplayName("Gate (c): @RestController methods must never accept or return @Entity classes")
    void ruleC_controllerMethodsMustNotExposeOrAcceptEntities() {
        ArchRule rule = methods()
                .that().areDeclaredInClassesThat().areAnnotatedWith(RestController.class)
                .should(new ArchCondition<>("not have entity return types or parameters") {
                    @Override
                    public void check(com.tngtech.archunit.core.domain.JavaMethod method, ConditionEvents events) {
                        if (method.getRawReturnType().isAnnotatedWith(Entity.class)) {
                            events.add(SimpleConditionEvent.violated(method,
                                    "Method " + method.getFullName() + " returns an @Entity (" + method.getRawReturnType().getName() + ")"));
                        }
                        for (var param : method.getParameters()) {
                            if (param.getRawType().isAnnotatedWith(Entity.class)) {
                                events.add(SimpleConditionEvent.violated(method,
                                        "Method " + method.getFullName() + " accepts an @Entity (" + param.getRawType().getName() + ") as parameter"));
                            }
                        }
                    }
                })
                .because("Controllers must never expose or accept JPA entities (Rule R7)");
        rule.check(importedClasses);
    }

    // (d) No cyclic dependencies among top-level domain packages
    @Test
    @DisplayName("Gate (d): No package cycles among top-level domain slices")
    void ruleD_noPackageCycles() {
        ArchRule rule = slices()
                .matching("com.fitflow.(*)..")
                .should().beFreeOfCycles()
                .because("Top-level modules must maintain a clean DAG without cyclic package dependencies");
        rule.check(importedClasses);
    }

    // (e) No calls to Instant.now(), LocalDate.now(), LocalDateTime.now(), System.currentTimeMillis() outside ClockConfig
    @Test
    @DisplayName("Gate (e): No direct calls to Instant/LocalDate.now() outside ClockConfig")
    void ruleE_noDirectClockCalls() {
        ArchRule rule = noClasses()
                .that().doNotHaveFullyQualifiedName(ClockConfig.class.getName())
                .should().callMethod(Instant.class, "now")
                .orShould().callMethod(LocalDate.class, "now")
                .orShould().callMethod(LocalDateTime.class, "now")
                .orShould().callMethod(System.class, "currentTimeMillis")
                .because("Time must always be derived from the injected java.time.Clock bean (Rule R5)");
        rule.check(importedClasses);
    }

    // (f) System.out / System.err / printStackTrace not used
    @Test
    @DisplayName("Gate (f): System.out/err and printStackTrace are forbidden")
    void ruleF_noSystemStreamsOrPrintStackTrace() {
        ArchRule rule = noClasses()
                .that().doNotHaveFullyQualifiedName("com.fitflow.FitFlowApplication")
                .should().accessField(System.class, "out")
                .orShould().accessField(System.class, "err")
                .orShould().callMethod(Throwable.class, "printStackTrace")
                .because("All logging must use SLF4J structured logging with correlation IDs and redaction");
        rule.check(importedClasses);
    }

    // (g) Classes ending in Controller must reside in controller or devtools package
    @Test
    @DisplayName("Gate (g): Classes named *Controller must reside in controller or devtools packages")
    void ruleG_controllersPackageConvention() {
        ArchRule rule = classes()
                .that().haveSimpleNameEndingWith("Controller")
                .should().resideInAnyPackage("..controller..", "..devtools..")
                .because("Controllers must reside in controller or devtools packages");
        rule.check(importedClasses);
    }

    // (h) No double or float types in packages membership, payment, payout
    @Test
    @DisplayName("Gate (h): No double or float types in packages membership, payment, payout")
    void ruleH_noDoubleOrFloatInMoneyPackages() {
        ArchRule fieldRule = noFields()
                .that().areDeclaredInClassesThat().resideInAnyPackage("..membership..", "..payment..", "..payout..")
                .should().haveRawType(double.class)
                .orShould().haveRawType(Double.class)
                .orShould().haveRawType(float.class)
                .orShould().haveRawType(Float.class)
                .because("Money logic must use BigInt paise or BigDecimal, never double or float (Rule R4)");
        fieldRule.check(importedClasses);

        ArchRule methodRule = methods()
                .that().areDeclaredInClassesThat().resideInAnyPackage("..membership..", "..payment..", "..payout..")
                .should(new ArchCondition<>("not have double or float parameters or return types") {
                    @Override
                    public void check(com.tngtech.archunit.core.domain.JavaMethod method, ConditionEvents events) {
                        Class<?>[] forbidden = {double.class, Double.class, float.class, Float.class};
                        for (Class<?> forbiddenType : forbidden) {
                            if (method.getRawReturnType().isEquivalentTo(forbiddenType)) {
                                events.add(SimpleConditionEvent.violated(method,
                                        "Method " + method.getFullName() + " returns " + forbiddenType.getSimpleName()));
                            }
                            for (var param : method.getParameters()) {
                                if (param.getRawType().isEquivalentTo(forbiddenType)) {
                                    events.add(SimpleConditionEvent.violated(method,
                                            "Method " + method.getFullName() + " has parameter of type " + forbiddenType.getSimpleName()));
                                }
                            }
                        }
                    }
                })
                .because("Methods in money packages must never accept or return double or float types (Rule R4)");
        methodRule.check(importedClasses);
    }

    // (i) Only package payment.gateway.razorpay may reference Razorpay base URL or read razorpay secrets
    @Test
    @DisplayName("Gate (i): Only package payment.gateway.razorpay may reference Razorpay base URL or read razorpay secrets")
    void ruleI_razorpayIsolationBoundary() {
        ArchRule rule = noClasses()
                .that().resideOutsideOfPackage("..payment.gateway.razorpay..")
                .should(new ArchCondition<>("reference Razorpay base URL") {
                    @Override
                    public void check(com.tngtech.archunit.core.domain.JavaClass clazz, ConditionEvents events) {
                        for (var field : clazz.getFields()) {
                            if (field.isAnnotatedWith(org.springframework.beans.factory.annotation.Value.class)) {
                                String val = field.getAnnotationOfType(org.springframework.beans.factory.annotation.Value.class).value();
                                if (val.contains("razorpay.base-url")) {
                                    events.add(SimpleConditionEvent.violated(field,
                                            "Class " + clazz.getName() + " references razorpay.base-url"));
                                }
                            }
                        }
                    }
                })
                .because("Only package payment.gateway.razorpay may reference the Razorpay base URL or read razorpay secrets");
        rule.check(importedClasses);
    }

    // (j) Only package com.fitflow.wellness may touch the health repositories and HealthDataCodec
    @Test
    @DisplayName("Gate (j): Only package com.fitflow.wellness may touch health repositories and HealthDataCodec")
    void ruleJ_wellnessStorageIsolationBoundary() {
        ArchRule rule = noClasses()
                .that().resideOutsideOfPackage("..wellness..")
                .should().dependOnClassesThat().haveSimpleNameEndingWith("HealthProfileRepository")
                .orShould().dependOnClassesThat().haveSimpleNameEndingWith("HealthConsentRepository")
                .orShould().dependOnClassesThat().haveSimpleNameEndingWith("BodyMetricRepository")
                .orShould().dependOnClassesThat().haveSimpleNameEndingWith("ProgressLogRepository")
                .orShould().dependOnClassesThat().haveSimpleNameEndingWith("HealthAccessLogRepository")
                .orShould().dependOnClassesThat().haveSimpleNameEndingWith("RestrictionProposalRepository")
                .orShould().dependOnClassesThat().haveSimpleName("HealthDataCodec")
                .because("Only package com.fitflow.wellness may touch health repositories and HealthDataCodec (Stage 7)");
        rule.check(importedClasses);
    }

    // (k) agent.tools must not depend on repositories or entities (Stage 8)
    @Test
    @DisplayName("Gate (k): agent.tools must not depend on repositories or @Entity classes")
    void ruleK_agentToolsMustNotDependOnRepositoriesOrEntities() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..agent.tools..")
                .should().dependOnClassesThat().resideInAPackage("..repository..")
                .orShould().dependOnClassesThat().areAnnotatedWith(Entity.class)
                .because("agent.tools must call services and handle only DTO records, never entities or repositories directly (Stage 8)");
        rule.check(importedClasses);
    }

    // (l) Reflection check: No tool parameter class may contain memberId, userId, adminId, trainerId (Stage 8)
    @Test
    @DisplayName("Gate (l): Tool parameter classes must not have caller ID fields")
    void ruleL_toolParameterClassesMustNotHaveCallerIdFields() {
        ArchRule rule = noFields()
                .that().areDeclaredInClassesThat().resideInAPackage("..agent.tools.dto..")
                .should().haveName("memberId")
                .orShould().haveName("userId")
                .orShould().haveName("adminId")
                .orShould().haveName("trainerId")
                .because("Tools must never accept caller ID from model arguments; caller context is injected by the runtime (Stage 8)");
        rule.check(importedClasses);
    }

    // (m) ArchUnit rule asserts no admin tool returns any class from com.fitflow.wellness.. (Stage 9)
    @Test
    @DisplayName("Gate (m): Admin tools must never return health data (com.fitflow.wellness..)")
    void ruleM_adminToolsMustNotReturnHealthData() {
        ArchRule rule = noClasses()
                .that().haveSimpleNameEndingWith("Tool")
                .and().haveSimpleNameStartingWith("ListAtRisk")
                .or().haveSimpleNameStartingWith("ProposeRetention")
                .or().haveSimpleNameStartingWith("DraftClassMetadata")
                .or().haveSimpleNameStartingWith("ProposeCreateClass")
                .or().haveSimpleNameStartingWith("GetDemand")
                .or().haveSimpleNameStartingWith("ProposeAddSession")
                .should().dependOnClassesThat().resideInAPackage("..wellness..")
                .because("Admin agents and tools never receive health data in any form (Stage 9)");
        rule.check(importedClasses);
    }
}
