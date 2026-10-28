package com.fitflow.booking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.booking.dto.BookingResponse;
import com.fitflow.booking.dto.CreateBookingRequest;
import com.fitflow.booking.entity.Booking;
import com.fitflow.booking.model.BookingStatus;
import com.fitflow.booking.model.WaitlistStatus;
import com.fitflow.booking.repository.AttendanceRecordRepository;
import com.fitflow.booking.repository.BookingRepository;
import com.fitflow.booking.repository.WaitlistEntryRepository;
import com.fitflow.fitnessclass.entity.FitnessClass;
import com.fitflow.fitnessclass.model.ClassImpactLevel;
import com.fitflow.fitnessclass.model.ClassIntensity;
import com.fitflow.fitnessclass.repository.FitnessClassRepository;
import com.fitflow.membership.MembershipService;
import com.fitflow.payout.repository.TrainerSessionEarningsRepository;
import com.fitflow.security.TokenService;
import com.fitflow.session.entity.ClassSession;
import com.fitflow.session.repository.ClassSessionRepository;
import com.fitflow.user.entity.User;
import com.fitflow.user.entity.UserRole;
import com.fitflow.user.repository.UserRepository;
import com.fitflow.user.repository.UserRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class BookingConcurrencyIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private WaitlistEntryRepository waitlistEntryRepository;

    @Autowired
    private ClassSessionRepository sessionRepository;

    @Autowired
    private FitnessClassRepository fitnessClassRepository;

    @Autowired
    private AttendanceRecordRepository attendanceRecordRepository;

    @Autowired
    private TrainerSessionEarningsRepository trainerSessionEarningsRepository;

    @Autowired
    private MembershipService membershipService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private Clock clock;

    private FitnessClass testClass;
    private User trainerUser;

    @BeforeEach
    void setUp() {
        waitlistEntryRepository.deleteAll();
        attendanceRecordRepository.deleteAll();
        trainerSessionEarningsRepository.deleteAll();
        bookingRepository.deleteAll();
        sessionRepository.deleteAll();
        fitnessClassRepository.deleteAll();

        long stamp = clock.millis();

        trainerUser = userRepository.save(User.builder()
                .email("trainer.conc." + stamp + "@test.com")
                .passwordHash("hashed")
                .fullName("Trainer Conc")
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .status(User.Status.ACTIVE)
                .build());
        userRoleRepository.save(new UserRole(new UserRole.UserRoleId(trainerUser.getId(), UserRole.Role.TRAINER)));

        testClass = fitnessClassRepository.save(FitnessClass.builder()
                .name("Concurrency HIIT " + stamp)
                .category("HIIT")
                .durationMinutes(60)
                .intensity(ClassIntensity.HIGH)
                .impactLevel(ClassImpactLevel.HIGH)
                .metValue(new BigDecimal("10.0"))
                .minAge(16)
                .defaultCapacity(5)
                .active(true)
                .metadataReviewed(true)
                .createdBy(trainerUser.getId())
                .build());
    }

    @Test
    @DisplayName("CONCURRENCY 1: 40 threads book 5-seat session with 40 distinct members (run 3x in a loop)")
    void testConcurrentBookingsAgainstFiveSeatsThreeTimes() throws Exception {
        for (int iteration = 1; iteration <= 3; iteration++) {
            runConcurrentBookingIteration(iteration);
        }
    }

    private void runConcurrentBookingIteration(int iteration) throws Exception {
        Instant startsAt = clock.instant().plus(Duration.ofDays(iteration + 1));
        Instant endsAt = startsAt.plus(Duration.ofMinutes(60));

        ClassSession session = sessionRepository.save(ClassSession.builder()
                .classId(testClass.getId())
                .trainerId(trainerUser.getId())
                .startsAt(startsAt)
                .endsAt(endsAt)
                .capacity(5)
                .bookedCount(0)
                .room("Room " + iteration)
                .status(ClassSession.Status.SCHEDULED)
                .trainerRateSnapshotPaise(90000L)
                .build());

        int numThreads = 40;
        List<User> members = new ArrayList<>(numThreads);
        List<String> tokens = new ArrayList<>(numThreads);

        for (int i = 0; i < numThreads; i++) {
            User member = userRepository.save(User.builder()
                    .email("m.conc." + iteration + "." + i + "." + clock.millis() + "@test.com")
                    .passwordHash("hashed")
                    .fullName("Member " + i)
                    .dateOfBirth(LocalDate.of(1995, 2, 10))
                    .status(User.Status.ACTIVE)
                    .build());
            userRoleRepository.save(new UserRole(new UserRole.UserRoleId(member.getId(), UserRole.Role.MEMBER)));
            membershipService.assignFreeMembershipIfMissing(member.getId());
            members.add(member);
            tokens.add(tokenService.generateAccessToken(member.getId(), member.getEmail(), List.of("MEMBER"), List.of()));
        }

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numThreads);

        AtomicInteger confirmedCount = new AtomicInteger(0);
        AtomicInteger waitlistedCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        ConcurrentLinkedQueue<String> errors = new ConcurrentLinkedQueue<>();

        CreateBookingRequest bookingRequest = new CreateBookingRequest(session.getId());
        String requestJson = objectMapper.writeValueAsString(bookingRequest);

        for (int i = 0; i < numThreads; i++) {
            final String token = tokens.get(i);
            final String idempotencyKey = "key-" + UUID.randomUUID();
            executor.submit(() -> {
                try {
                    startLatch.await();
                    MvcResult result = mockMvc.perform(post("/api/v1/bookings")
                                    .header("Authorization", "Bearer " + token)
                                    .header("Idempotency-Key", idempotencyKey)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(requestJson))
                            .andReturn();

                    int status = result.getResponse().getStatus();
                    if (status == 201) {
                        confirmedCount.incrementAndGet();
                    } else if (status == 202) {
                        waitlistedCount.incrementAndGet();
                    } else {
                        errorCount.incrementAndGet();
                        errors.add("Status: " + status + " Body: " + result.getResponse().getContentAsString());
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    errors.add("Exception: " + e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finished = doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(finished, "Iteration " + iteration + " timed out waiting for concurrent bookings");
        assertTrue(errors.isEmpty(), "Unexpected errors in iteration " + iteration + ": " + errors);

        // Exact assertions per Stage 4 specification
        assertEquals(5, confirmedCount.get(), "Iteration " + iteration + ": Exactly 5 bookings must be CONFIRMED");
        assertEquals(35, waitlistedCount.get(), "Iteration " + iteration + ": Exactly 35 bookings must be WAITLISTED");
        assertEquals(0, errorCount.get(), "Iteration " + iteration + ": There should be 0 failed requests");

        ClassSession refreshed = sessionRepository.findById(session.getId()).orElseThrow();
        assertEquals(5, refreshed.getBookedCount(), "Iteration " + iteration + ": booked_count must be exactly 5");
        assertTrue(refreshed.getBookedCount() >= 0, "Iteration " + iteration + ": booked_count must not be negative");

        // Quota assertions: exactly 5 confirmed members consumed 1 session each; 35 waitlisted consumed 0
        long totalConfirmedInDb = bookingRepository.findBySessionIdAndStatus(session.getId(), BookingStatus.CONFIRMED).size();
        assertEquals(5, totalConfirmedInDb, "Total CONFIRMED bookings in DB must be 5");

        int quotaConsumedCount = 0;
        for (User m : members) {
            long used = bookingRepository.countByMemberIdAndStatusIn(
                    m.getId(),
                    Set.of(BookingStatus.CONFIRMED, BookingStatus.ATTENDED, BookingStatus.NO_SHOW)
            );
            assertTrue(used <= 1, "No member should have consumed quota more than once");
            if (used == 1) {
                quotaConsumedCount++;
            }
        }
        assertEquals(5, quotaConsumedCount, "Stub entitlement quota must be consumed exactly 5 times across the 40 members");
    }

    @Test
    @DisplayName("CONCURRENCY 2: 10 threads cancel same booking simultaneously -> exactly 1 succeeds, 1 promotion")
    void testConcurrentCancellationOfSameBooking() throws Exception {
        Instant startsAt = clock.instant().plus(Duration.ofDays(3));
        Instant endsAt = startsAt.plus(Duration.ofMinutes(60));

        ClassSession session = sessionRepository.save(ClassSession.builder()
                .classId(testClass.getId())
                .trainerId(trainerUser.getId())
                .startsAt(startsAt)
                .endsAt(endsAt)
                .capacity(1)
                .bookedCount(1)
                .room("Room Solo")
                .status(ClassSession.Status.SCHEDULED)
                .trainerRateSnapshotPaise(80000L)
                .build());

        // Member 1 holds the confirmed booking
        long stamp = clock.millis();
        User member1 = userRepository.save(User.builder()
                .email("m1.cancel." + stamp + "@test.com")
                .passwordHash("hashed")
                .fullName("Member One")
                .dateOfBirth(LocalDate.of(1992, 3, 1))
                .status(User.Status.ACTIVE)
                .build());
        userRoleRepository.save(new UserRole(new UserRole.UserRoleId(member1.getId(), UserRole.Role.MEMBER)));
        membershipService.assignFreeMembershipIfMissing(member1.getId());
        String member1Token = tokenService.generateAccessToken(member1.getId(), member1.getEmail(), List.of("MEMBER"), List.of());

        Booking booking = bookingRepository.save(Booking.builder()
                .sessionId(session.getId())
                .memberId(member1.getId())
                .status(BookingStatus.CONFIRMED)
                .consumedFrom(com.fitflow.booking.model.ConsumedFrom.FREE_SESSION)
                .idempotencyKey("idemp-" + UUID.randomUUID())
                .bookedAt(clock.instant())
                .activeFlag((byte) 1)
                .version(0)
                .createdAt(clock.instant())
                .build());

        // Member 2 is on the waitlist
        User member2 = userRepository.save(User.builder()
                .email("m2.wait." + stamp + "@test.com")
                .passwordHash("hashed")
                .fullName("Member Two")
                .dateOfBirth(LocalDate.of(1994, 7, 15))
                .status(User.Status.ACTIVE)
                .build());
        userRoleRepository.save(new UserRole(new UserRole.UserRoleId(member2.getId(), UserRole.Role.MEMBER)));
        membershipService.assignFreeMembershipIfMissing(member2.getId());

        waitlistEntryRepository.save(com.fitflow.booking.entity.WaitlistEntry.builder()
                .sessionId(session.getId())
                .memberId(member2.getId())
                .status(WaitlistStatus.WAITING)
                .createdAt(clock.instant())
                .build());

        int numThreads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        ConcurrentLinkedQueue<Integer> statuses = new ConcurrentLinkedQueue<>();

        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    MvcResult result = mockMvc.perform(delete("/api/v1/bookings/" + booking.getId())
                                    .header("Authorization", "Bearer " + member1Token))
                            .andReturn();

                    int status = result.getResponse().getStatus();
                    statuses.add(status);
                    if (status == 204) {
                        successCount.incrementAndGet();
                    } else if (status == 409) {
                        conflictCount.incrementAndGet();
                    }
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finished = doneLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(finished, "Concurrent cancellation timed out");

        // Assertions per Stage 4 specification:
        // Exactly ONE cancel succeeds (204), 9 conflict (409)
        assertEquals(1, successCount.get(), "Exactly one cancel must succeed (204). Statuses: " + statuses);
        assertEquals(9, conflictCount.get(), "The remaining 9 cancels must fail with 409. Statuses: " + statuses);

        // Booking is CANCELLED
        Booking refreshedBooking = bookingRepository.findById(booking.getId()).orElseThrow();
        assertEquals(BookingStatus.CANCELLED, refreshedBooking.getStatus());

        // Waitlist candidate member 2 is PROMOTED
        var refreshedWaitlist = waitlistEntryRepository.findBySessionIdAndMemberId(session.getId(), member2.getId()).orElseThrow();
        assertEquals(WaitlistStatus.PROMOTED, refreshedWaitlist.getStatus(), "Member 2 must be PROMOTED");

        // Exactly one promotion occurred, so booked_count remains 1 (1 decremented + 1 allocated on promotion)
        ClassSession refreshedSession = sessionRepository.findById(session.getId()).orElseThrow();
        assertEquals(1, refreshedSession.getBookedCount(), "booked_count must be 1 after cancel + waitlist promotion");

        // Confirm new booking exists for member 2
        var member2Booking = bookingRepository.findBySessionIdAndMemberIdAndActiveFlag(session.getId(), member2.getId(), (byte) 1);
        assertTrue(member2Booking.isPresent(), "Member 2 must have an active CONFIRMED booking");
        assertEquals(BookingStatus.CONFIRMED, member2Booking.get().getStatus());
    }
}
