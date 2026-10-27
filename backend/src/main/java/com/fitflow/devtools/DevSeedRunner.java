package com.fitflow.devtools;

import com.fitflow.fitnessclass.entity.FitnessClass;
import com.fitflow.fitnessclass.repository.FitnessClassRepository;
import com.fitflow.session.entity.ClassSession;
import com.fitflow.session.repository.ClassSessionRepository;
import com.fitflow.trainer.entity.TrainerProfile;
import com.fitflow.trainer.repository.TrainerProfileRepository;
import com.fitflow.user.entity.AdminPermission;
import com.fitflow.user.entity.User;
import com.fitflow.user.entity.UserRole;
import com.fitflow.user.repository.AdminPermissionRepository;
import com.fitflow.user.repository.UserRepository;
import com.fitflow.user.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevSeedRunner implements ApplicationRunner {

    public static final String DEV_DEFAULT_PASSWORD = "FitFlowDev2026!";

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final AdminPermissionRepository adminPermissionRepository;
    private final TrainerProfileRepository trainerProfileRepository;
    private final FitnessClassRepository fitnessClassRepository;
    private final ClassSessionRepository classSessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("Running dev seed runner...");
        Instant now = clock.instant();

        // 1. Admin User
        User admin = userRepository.findByEmail("dev@fitflow.local").orElseGet(() -> {
            User u = userRepository.save(User.builder()
                    .email("dev@fitflow.local")
                    .fullName("Dev Administrator")
                    .passwordHash(passwordEncoder.encode(DEV_DEFAULT_PASSWORD))
                    .mustChangePassword(true)
                    .status(User.Status.ACTIVE)
                    .build());

            userRoleRepository.save(UserRole.builder()
                    .id(new UserRole.UserRoleId(u.getId(), UserRole.Role.ADMIN))
                    .build());

            adminPermissionRepository.save(AdminPermission.builder()
                    .id(new AdminPermission.AdminPermissionId(u.getId(), "SUPER_ADMIN"))
                    .grantedBy(u.getId())
                    .grantedAt(now)
                    .build());

            log.info("Seeded dev admin user dev@fitflow.local with SUPER_ADMIN permission");
            return u;
        });

        // 2. Trainers
        User alex = userRepository.findByEmail("trainer.alex@fitflow.local").orElseGet(() -> {
            User u = userRepository.save(User.builder()
                    .email("trainer.alex@fitflow.local")
                    .fullName("Alex Rivers")
                    .passwordHash(passwordEncoder.encode(DEV_DEFAULT_PASSWORD))
                    .status(User.Status.ACTIVE)
                    .build());

            userRoleRepository.save(UserRole.builder()
                    .id(new UserRole.UserRoleId(u.getId(), UserRole.Role.TRAINER))
                    .build());

            trainerProfileRepository.save(TrainerProfile.builder()
                    .userId(u.getId())
                    .bio("Elite strength and mobility coach with 8+ years experience.")
                    .specialties("[\"Strength\", \"Mobility\", \"HIIT\"]")
                    .certifications("[\"CSCS\", \"NASM-CPT\"]")
                    .ratePerSessionPaise(250000L) // Rs. 2,500.00
                    .active(true)
                    .build());
            return u;
        });

        User sam = userRepository.findByEmail("trainer.sam@fitflow.local").orElseGet(() -> {
            User u = userRepository.save(User.builder()
                    .email("trainer.sam@fitflow.local")
                    .fullName("Samantha Ray")
                    .passwordHash(passwordEncoder.encode(DEV_DEFAULT_PASSWORD))
                    .status(User.Status.ACTIVE)
                    .build());

            userRoleRepository.save(UserRole.builder()
                    .id(new UserRole.UserRoleId(u.getId(), UserRole.Role.TRAINER))
                    .build());

            trainerProfileRepository.save(TrainerProfile.builder()
                    .userId(u.getId())
                    .bio("Specialist in functional yoga flow, breathwork, and rehabilitation.")
                    .specialties("[\"Yoga\", \"Pilates\", \"Breathwork\"]")
                    .certifications("[\"RYT-500\", \"PMA-CPT\"]")
                    .ratePerSessionPaise(220000L) // Rs. 2,200.00
                    .active(true)
                    .build());
            return u;
        });

        // 3. 8 Sample Classes
        seedSampleClasses(admin.getId());

        // 4. Sample Sessions for the next 14 days
        seedSampleSessions(alex.getId(), sam.getId(), now);
    }

    private void seedSampleClasses(Long adminId) {
        createClassIfMissing("Power Vinyasa Yoga", "Dynamic yoga linking breath to vigorous movement", "Yoga", 60,
                FitnessClass.Intensity.MODERATE, FitnessClass.ImpactLevel.LOW, new BigDecimal("4.0"),
                "[\"KNEE_PAIN\", \"WRIST_PAIN\"]", 14, 25, adminId);

        createClassIfMissing("HIIT Blast", "High intensity interval training with plyometric bursts", "Cardio", 45,
                FitnessClass.Intensity.HIGH, FitnessClass.ImpactLevel.HIGH, new BigDecimal("9.5"),
                "[\"HEART_CONDITION\", \"HYPERTENSION\", \"PREGNANCY\"]", 16, 20, adminId);

        createClassIfMissing("Gentle Hatha Flow", "Foundational postures with long holds and alignment focus", "Mobility", 50,
                FitnessClass.Intensity.LOW, FitnessClass.ImpactLevel.LOW, new BigDecimal("2.5"),
                "[]", 12, 30, adminId);

        createClassIfMissing("Spin & Cadence", "High-energy indoor cycling with sprints and hill climbs", "Cycling", 45,
                FitnessClass.Intensity.HIGH, FitnessClass.ImpactLevel.LOW, new BigDecimal("8.0"),
                "[\"ASTHMA\"]", 14, 25, adminId);

        createClassIfMissing("Core & Functional Strength", "Compound functional resistance training", "Strength", 55,
                FitnessClass.Intensity.MODERATE, FitnessClass.ImpactLevel.MEDIUM, new BigDecimal("5.5"),
                "[\"LOWER_BACK_PAIN\"]", 14, 20, adminId);

        createClassIfMissing("BoxFit Circuit", "Non-contact boxing drills and heavy bag combinations", "Combat", 50,
                FitnessClass.Intensity.HIGH, FitnessClass.ImpactLevel.HIGH, new BigDecimal("8.5"),
                "[\"SHOULDER_IMPINGEMENT\"]", 16, 18, adminId);

        createClassIfMissing("Pilates Reform & Mat", "Core stability, pelvic floor, and spine alignment", "Pilates", 45,
                FitnessClass.Intensity.MODERATE, FitnessClass.ImpactLevel.LOW, new BigDecimal("3.8"),
                "[\"NECK_SPRAIN\"]", 14, 15, adminId);

        createClassIfMissing("Deep Stretch & Breathwork", "Restorative myofascial release and pranayama", "Recovery", 40,
                FitnessClass.Intensity.LOW, FitnessClass.ImpactLevel.LOW, new BigDecimal("1.8"),
                "[]", 12, 30, adminId);
    }

    private void createClassIfMissing(String name, String description, String category, int durationMinutes,
                                      FitnessClass.Intensity intensity, FitnessClass.ImpactLevel impactLevel,
                                      BigDecimal metValue, String tags, int minAge, int capacity, Long creatorId) {
        if (!fitnessClassRepository.existsByName(name)) {
            fitnessClassRepository.save(FitnessClass.builder()
                    .name(name)
                    .description(description)
                    .category(category)
                    .durationMinutes(durationMinutes)
                    .intensity(intensity)
                    .impactLevel(impactLevel)
                    .metValue(metValue)
                    .contraindicationTags(tags)
                    .minAge(minAge)
                    .defaultCapacity(capacity)
                    .active(true)
                    .metadataReviewed(true)
                    .createdBy(creatorId)
                    .build());
        }
    }

    private void seedSampleSessions(Long alexId, Long samId, Instant baseTime) {
        List<FitnessClass> classes = fitnessClassRepository.findAll();
        if (classes.isEmpty() || !classSessionRepository.findAll().isEmpty()) {
            return;
        }

        FitnessClass yoga = classes.stream().filter(c -> c.getName().contains("Yoga")).findFirst().orElse(classes.get(0));
        FitnessClass hiit = classes.stream().filter(c -> c.getName().contains("HIIT")).findFirst().orElse(classes.get(1));

        for (int day = 1; day <= 14; day++) {
            Instant sessionStartMorning = baseTime.plus(day, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS).plus(7, ChronoUnit.HOURS);
            Instant sessionStartEvening = baseTime.plus(day, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS).plus(18, ChronoUnit.HOURS);

            classSessionRepository.save(ClassSession.builder()
                    .classId(yoga.getId())
                    .trainerId(samId)
                    .startsAt(sessionStartMorning)
                    .endsAt(sessionStartMorning.plus(yoga.getDurationMinutes(), ChronoUnit.MINUTES))
                    .capacity(yoga.getDefaultCapacity())
                    .bookedCount(0)
                    .room("Studio A - Lotus")
                    .status(ClassSession.Status.SCHEDULED)
                    .trainerRateSnapshotPaise(220000L)
                    .build());

            classSessionRepository.save(ClassSession.builder()
                    .classId(hiit.getId())
                    .trainerId(alexId)
                    .startsAt(sessionStartEvening)
                    .endsAt(sessionStartEvening.plus(hiit.getDurationMinutes(), ChronoUnit.MINUTES))
                    .capacity(hiit.getDefaultCapacity())
                    .bookedCount(0)
                    .room("Studio B - Iron")
                    .status(ClassSession.Status.SCHEDULED)
                    .trainerRateSnapshotPaise(250000L)
                    .build());
        }
        log.info("Seeded 28 sessions over the next 14 days");
    }
}
