package com.fitflow.booking;

import com.fitflow.booking.dto.BookingResponse;
import com.fitflow.booking.dto.CreateBookingRequest;
import com.fitflow.booking.dto.MemberBookingResponse;
import com.fitflow.booking.entity.Booking;
import com.fitflow.booking.entity.WaitlistEntry;
import com.fitflow.booking.model.BookingStatus;
import com.fitflow.booking.model.WaitlistStatus;
import com.fitflow.booking.repository.BookingRepository;
import com.fitflow.booking.repository.WaitlistEntryRepository;
import com.fitflow.common.error.ApiException;
import com.fitflow.common.pagination.PageResponse;
import com.fitflow.fitnessclass.entity.FitnessClass;
import com.fitflow.fitnessclass.repository.FitnessClassRepository;
import com.fitflow.notification.OutboxService;
import com.fitflow.session.SessionBookingHandler;
import com.fitflow.session.entity.ClassSession;
import com.fitflow.session.repository.ClassSessionRepository;
import com.fitflow.user.entity.User;
import com.fitflow.user.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
public class BookingService implements SessionBookingHandler {

    private final BookingRepository bookingRepository;
    private final WaitlistEntryRepository waitlistEntryRepository;
    private final ClassSessionRepository classSessionRepository;
    private final FitnessClassRepository fitnessClassRepository;
    private final UserRepository userRepository;
    private final EntitlementService entitlementService;
    private final IdempotencyService idempotencyService;
    private final OutboxService outboxService;
    private final Clock clock;
    private final boolean waitlistEnabled;
    private final int cancelCutoffHours;
    private final String displayTimezone;

    // Test hook for atomicity testing
    private static volatile boolean atomicityFailureSimulation = false;

    public static void setAtomicityFailureSimulation(boolean enable) {
        atomicityFailureSimulation = enable;
    }

    public BookingService(
            BookingRepository bookingRepository,
            WaitlistEntryRepository waitlistEntryRepository,
            ClassSessionRepository classSessionRepository,
            FitnessClassRepository fitnessClassRepository,
            UserRepository userRepository,
            EntitlementService entitlementService,
            IdempotencyService idempotencyService,
            OutboxService outboxService,
            Clock clock,
            @Value("${fitflow.booking.waitlist-enabled:true}") boolean waitlistEnabled,
            @Value("${fitflow.booking.cancel-cutoff-hours:4}") int cancelCutoffHours,
            @Value("${fitflow.display-timezone:Asia/Kolkata}") String displayTimezone) {
        this.bookingRepository = bookingRepository;
        this.waitlistEntryRepository = waitlistEntryRepository;
        this.classSessionRepository = classSessionRepository;
        this.fitnessClassRepository = fitnessClassRepository;
        this.userRepository = userRepository;
        this.entitlementService = entitlementService;
        this.idempotencyService = idempotencyService;
        this.outboxService = outboxService;
        this.clock = clock;
        this.waitlistEnabled = waitlistEnabled;
        this.cancelCutoffHours = cancelCutoffHours;
        this.displayTimezone = displayTimezone;
    }

    // =========================================================================
    // PART C: Booking Creation
    // =========================================================================

    @Transactional
    public BookingResult createBooking(CreateBookingRequest request, String idempotencyKey, Long memberId) {
        idempotencyService.validateKey(idempotencyKey);
        String requestHash = idempotencyService.computeHash(request);

        // Check Idempotency Record
        Optional<IdempotencyService.CachedIdempotentResponse> cached =
                idempotencyService.check(memberId, idempotencyKey, requestHash);
        if (cached.isPresent()) {
            IdempotencyService.CachedIdempotentResponse hit = cached.get();
            log.info("Returning cached idempotent booking response for memberId={}, status={}", memberId, hit.status());
            return new BookingResult(hit.status(), hit.payload(), true);
        }

        // Rule 1: Session exists and is SCHEDULED, starts in future
        ClassSession session = classSessionRepository.findById(request.sessionId())
                .orElseThrow(() -> ApiException.notFound("Session not found with id: " + request.sessionId()));

        if (session.getStatus() != ClassSession.Status.SCHEDULED) {
            throw ApiException.bookingRuleViolation("Session is not open for booking (status: " + session.getStatus() + ")");
        }

        Instant now = clock.instant();
        if (!session.getStartsAt().isAfter(now)) {
            throw ApiException.bookingRuleViolation("Cannot book a session that has already started or is in the past");
        }

        // Rule 2: Member age >= class.minAge
        FitnessClass fitnessClass = fitnessClassRepository.findById(session.getClassId())
                .orElseThrow(() -> ApiException.notFound("Class definition not found with id: " + session.getClassId()));

        User member = userRepository.findById(memberId)
                .orElseThrow(() -> ApiException.notFound("User not found with id: " + memberId));

        if (member.getDateOfBirth() == null) {
            if (fitnessClass.getMinAge() > 14) {
                throw ApiException.ageRestrictedForbidden("Please add your date of birth in your profile to book this class");
            }
        } else {
            LocalDate today = LocalDate.ofInstant(now, ZoneId.of(displayTimezone));
            int age = Period.between(member.getDateOfBirth(), today).getYears();
            if (age < fitnessClass.getMinAge()) {
                throw ApiException.ageRestrictedForbidden("Member does not meet the minimum age requirement of " + fitnessClass.getMinAge());
            }
        }

        // Rule 3: Member has no other CONFIRMED booking overlapping the time window
        List<Booking> overlapping = bookingRepository.findOverlappingConfirmedBookings(
                memberId, session.getStartsAt(), session.getEndsAt()
        );
        if (!overlapping.isEmpty()) {
            throw ApiException.bookingRuleViolation("Member already has an active confirmed booking during this session's time window");
        }

        // Rule 4: Entitlement reserve
        Entitlement entitlement = entitlementService.reserve(memberId, session.getId(), idempotencyKey);

        // Rule 5: Seat allocation: atomic UPDATE class_sessions SET booked_count = booked_count + 1
        int allocated = classSessionRepository.allocateSeatAtomic(session.getId());

        if (allocated == 1) {
            // Seat secured
            Booking booking = Booking.builder()
                    .sessionId(session.getId())
                    .memberId(memberId)
                    .status(BookingStatus.CONFIRMED)
                    .consumedFrom(entitlement.consumedFrom())
                    .membershipId(entitlement.membershipId())
                    .idempotencyKey(idempotencyKey)
                    .bookedAt(now)
                    .activeFlag((byte) 1)
                    .version(0)
                    .createdAt(now)
                    .build();

            Booking savedBooking = bookingRepository.saveAndFlush(booking);

            // Atomicity test hook
            if (atomicityFailureSimulation) {
                throw new RuntimeException("Simulated exception after booking insert for atomicity rollback verification");
            }

            // Emit booking.created
            Map<String, Object> outboxPayload = Map.of(
                    "bookingId", savedBooking.getId(),
                    "memberId", memberId,
                    "sessionId", session.getId(),
                    "startsAt", session.getStartsAt().toString()
            );
            outboxService.append("booking", savedBooking.getId(), "booking.created", outboxPayload);

            BookingResponse responseDto = BookingResponse.confirmed(savedBooking.getId(), session.getId());
            idempotencyService.record(memberId, idempotencyKey, requestHash, 201, responseDto);

            log.info("Confirmed booking id={} created for memberId={} in sessionId={}",
                    savedBooking.getId(), memberId, session.getId());
            return new BookingResult(201, responseDto, false);
        } else {
            // Session full
            // Restore reserved entitlement
            entitlementService.restoreEntitlement(entitlement.membershipId(), "Session full; reservation released");

            if (!waitlistEnabled) {
                throw ApiException.sessionFull("Session is full and waitlist is currently disabled");
            }

            // Check if already in waitlist
            Optional<WaitlistEntry> existingWaitlist = waitlistEntryRepository
                    .findBySessionIdAndMemberIdAndStatus(session.getId(), memberId, WaitlistStatus.WAITING);

            int position;
            if (existingWaitlist.isPresent()) {
                position = (int) waitlistEntryRepository.countBySessionIdAndStatusAndCreatedAtLessThanEqual(
                        session.getId(), WaitlistStatus.WAITING, existingWaitlist.get().getCreatedAt()
                );
            } else {
                WaitlistEntry entry = WaitlistEntry.builder()
                        .sessionId(session.getId())
                        .memberId(memberId)
                        .status(WaitlistStatus.WAITING)
                        .createdAt(now)
                        .build();
                WaitlistEntry savedEntry = waitlistEntryRepository.saveAndFlush(entry);
                position = (int) waitlistEntryRepository.countBySessionIdAndStatusAndCreatedAtLessThanEqual(
                        session.getId(), WaitlistStatus.WAITING, savedEntry.getCreatedAt()
                );
            }

            BookingResponse responseDto = BookingResponse.waitlisted(session.getId(), position);
            idempotencyService.record(memberId, idempotencyKey, requestHash, 202, responseDto);

            log.info("Member id={} waitlisted for sessionId={} at position={}", memberId, session.getId(), position);
            return new BookingResult(202, responseDto, false);
        }
    }

    // =========================================================================
    // PART C: Cancellation
    // =========================================================================

    @Transactional
    public void cancelBooking(Long bookingId, Long callerId, boolean isAdmin, String reason) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> ApiException.notFound("Booking not found with id: " + bookingId));

        if (!isAdmin && !booking.getMemberId().equals(callerId)) {
            // Return 404 to avoid leaking IDs
            throw ApiException.notFound("Booking not found with id: " + bookingId);
        }

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw ApiException.bookingRuleViolation("Booking is already cancelled");
        }

        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw ApiException.bookingRuleViolation("Cannot cancel booking in status " + booking.getStatus());
        }

        ClassSession session = classSessionRepository.findById(booking.getSessionId())
                .orElseThrow(() -> ApiException.notFound("Session not found with id: " + booking.getSessionId()));

        Instant now = clock.instant();
        Instant cutoff = session.getStartsAt().minus(Duration.ofHours(cancelCutoffHours));
        boolean beforeCutoff = !now.isAfter(cutoff);

        if (!isAdmin && !beforeCutoff) {
            throw ApiException.bookingRuleViolation(
                    "Cancellation deadline has passed (cutoff is " + cancelCutoffHours + " hours before session start)"
            );
        }

        // Update booking
        booking.setStatus(BookingStatus.CANCELLED);
        booking.setActiveFlag(null);
        booking.setCancelledAt(now);
        bookingRepository.saveAndFlush(booking);

        // Atomically decrement seat
        classSessionRepository.releaseSeatAtomic(session.getId());

        // Restore entitlement if before cutoff or admin
        if (isAdmin || beforeCutoff) {
            entitlementService.restore(
                    booking.getId(),
                    isAdmin ? (reason != null ? reason : "Admin cancellation") : "Member cancellation before cutoff"
            );
        }

        // Emit booking.cancelled
        Map<String, Object> outboxPayload = Map.of(
                "bookingId", booking.getId(),
                "memberId", booking.getMemberId(),
                "sessionId", session.getId(),
                "cancelledBy", isAdmin ? "ADMIN" : "MEMBER"
        );
        outboxService.append("booking", booking.getId(), "booking.cancelled", outboxPayload);

        log.info("Booking id={} cancelled. Admin={}, restoredEntitlement={}",
                bookingId, isAdmin, isAdmin || beforeCutoff);

        // FIFO Waitlist Promotion
        promoteNextWaitlistCandidate(session, now);
    }

    private void promoteNextWaitlistCandidate(ClassSession session, Instant now) {
        List<WaitlistEntry> waitingList = waitlistEntryRepository
                .findBySessionIdAndStatusOrderByCreatedAtAscIdAsc(session.getId(), WaitlistStatus.WAITING);

        for (WaitlistEntry candidate : waitingList) {
            Long candidateMemberId = candidate.getMemberId();

            // 1. Check candidate schedule overlap
            List<Booking> overlapping = bookingRepository.findOverlappingConfirmedBookings(
                    candidateMemberId, session.getStartsAt(), session.getEndsAt()
            );
            if (!overlapping.isEmpty()) {
                log.info("Waitlist candidate memberId={} has schedule overlap during promotion, marked LEFT",
                        candidateMemberId);
                candidate.setStatus(WaitlistStatus.LEFT);
                waitlistEntryRepository.save(candidate);
                continue;
            }

            // 2. Check candidate entitlement
            Entitlement candidateEntitlement;
            try {
                candidateEntitlement = entitlementService.reserve(
                        candidateMemberId, session.getId(), UUID.randomUUID().toString()
                );
            } catch (Exception ex) {
                log.info("Waitlist candidate memberId={} failed entitlement reservation during promotion, marked LEFT",
                        candidateMemberId);
                candidate.setStatus(WaitlistStatus.LEFT);
                waitlistEntryRepository.save(candidate);
                continue;
            }

            // 3. Candidate is eligible -> Allocate seat
            int seatAllocated = classSessionRepository.allocateSeatAtomic(session.getId());
            if (seatAllocated == 1) {
                Booking promotedBooking = Booking.builder()
                        .sessionId(session.getId())
                        .memberId(candidateMemberId)
                        .status(BookingStatus.CONFIRMED)
                        .consumedFrom(candidateEntitlement.consumedFrom())
                        .membershipId(candidateEntitlement.membershipId())
                        .idempotencyKey(UUID.randomUUID().toString())
                        .bookedAt(now)
                        .activeFlag((byte) 1)
                        .version(0)
                        .createdAt(now)
                        .build();

                promotedBooking = bookingRepository.saveAndFlush(promotedBooking);

                candidate.setStatus(WaitlistStatus.PROMOTED);
                waitlistEntryRepository.save(candidate);

                // Emit booking.created
                outboxService.append("booking", promotedBooking.getId(), "booking.created", Map.of(
                        "bookingId", promotedBooking.getId(),
                        "memberId", candidateMemberId,
                        "sessionId", session.getId(),
                        "startsAt", session.getStartsAt().toString()
                ));

                // Emit waitlist.promoted
                outboxService.append("booking", promotedBooking.getId(), "waitlist.promoted", Map.of(
                        "sessionId", session.getId(),
                        "memberId", candidateMemberId,
                        "bookingId", promotedBooking.getId()
                ));

                log.info("Successfully promoted waitlist memberId={} to bookingId={} in sessionId={}",
                        candidateMemberId, promotedBooking.getId(), session.getId());
                break; // Exactly one seat freed, so promote exactly one candidate
            } else {
                entitlementService.restoreEntitlement(candidateEntitlement.membershipId(), "Seat allocation failed during promotion");
                break;
            }
        }
    }

    // =========================================================================
    // PART C: Leave Waitlist & Member Bookings
    // =========================================================================

    @Transactional
    public void leaveWaitlist(Long sessionId, Long memberId) {
        WaitlistEntry entry = waitlistEntryRepository
                .findBySessionIdAndMemberIdAndStatus(sessionId, memberId, WaitlistStatus.WAITING)
                .orElseThrow(() -> ApiException.notFound("Member is not on the waitlist for session id: " + sessionId));

        entry.setStatus(WaitlistStatus.LEFT);
        waitlistEntryRepository.save(entry);
        log.info("Member id={} left waitlist for sessionId={}", memberId, sessionId);
    }

    @Transactional(readOnly = true)
    public PageResponse<MemberBookingResponse> getMemberBookings(
            Long memberId,
            BookingStatus status,
            Instant from,
            Instant to,
            int page,
            int size) {

        int resolvedSize = Math.min(Math.max(size, 1), 100);
        Page<Booking> bookingPage = bookingRepository.findMemberBookings(
                memberId, status, from, to, PageRequest.of(page, resolvedSize)
        );

        Map<Long, ClassSession> sessionMap = new HashMap<>();
        Map<Long, FitnessClass> classMap = new HashMap<>();
        Map<Long, User> trainerMap = new HashMap<>();

        List<MemberBookingResponse> items = bookingPage.getContent().stream()
                .map(booking -> {
                    ClassSession session = sessionMap.computeIfAbsent(
                            booking.getSessionId(),
                            id -> classSessionRepository.findById(id).orElse(null)
                    );

                    String className = "Fitness Class";
                    String category = "GENERAL";
                    Instant startsAt = null;
                    Instant endsAt = null;
                    String room = "Room";
                    String trainerName = "Trainer";

                    if (session != null) {
                        startsAt = session.getStartsAt();
                        endsAt = session.getEndsAt();
                        room = session.getRoom();

                        FitnessClass fc = classMap.computeIfAbsent(
                                session.getClassId(),
                                id -> fitnessClassRepository.findById(id).orElse(null)
                        );
                        if (fc != null) {
                            className = fc.getName();
                            category = fc.getCategory();
                        }

                        User trainer = trainerMap.computeIfAbsent(
                                session.getTrainerId(),
                                id -> userRepository.findById(id).orElse(null)
                        );
                        if (trainer != null) {
                            trainerName = trainer.getFullName();
                        }
                    }

                    return new MemberBookingResponse(
                            booking.getId(),
                            booking.getSessionId(),
                            className,
                            category,
                            startsAt,
                            endsAt,
                            room,
                            trainerName,
                            booking.getStatus().name(),
                            booking.getBookedAt(),
                            booking.getCancelledAt()
                    );
                })
                .toList();

        return new PageResponse<>(
                items,
                bookingPage.getNumber(),
                bookingPage.getSize(),
                bookingPage.getTotalElements(),
                bookingPage.getTotalPages()
        );
    }

    // =========================================================================
    // SessionBookingHandler implementation (DIP pattern)
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public String getMemberBookingStatus(Long sessionId, Long memberId) {
        if (memberId == null) {
            return null;
        }
        Optional<Booking> active = bookingRepository
                .findBySessionIdAndMemberIdAndActiveFlag(sessionId, memberId, (byte) 1);
        if (active.isPresent()) {
            return active.get().getStatus().name();
        }
        Optional<WaitlistEntry> waitlist = waitlistEntryRepository
                .findBySessionIdAndMemberIdAndStatus(sessionId, memberId, WaitlistStatus.WAITING);
        if (waitlist.isPresent()) {
            return "WAITLISTED";
        }
        return null;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, String> getMemberBookingStatuses(List<Long> sessionIds, Long memberId) {
        if (memberId == null || sessionIds == null || sessionIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> result = new HashMap<>();

        List<Booking> bookings = bookingRepository.findByMemberIdAndSessionIdIn(memberId, sessionIds);
        for (Booking b : bookings) {
            if (b.getActiveFlag() != null && b.getActiveFlag() == (byte) 1) {
                result.put(b.getSessionId(), b.getStatus().name());
            }
        }

        for (Long sid : sessionIds) {
            if (!result.containsKey(sid)) {
                Optional<WaitlistEntry> waitlist = waitlistEntryRepository
                        .findBySessionIdAndMemberIdAndStatus(sid, memberId, WaitlistStatus.WAITING);
                if (waitlist.isPresent()) {
                    result.put(sid, "WAITLISTED");
                }
            }
        }
        return result;
    }

    @Override
    @Transactional
    public int cancelConfirmedBookingsForSession(Long sessionId, String reason) {
        List<Booking> confirmed = bookingRepository.findBySessionIdAndStatus(sessionId, BookingStatus.CONFIRMED);
        Instant now = clock.instant();

        for (Booking booking : confirmed) {
            booking.setStatus(BookingStatus.CANCELLED);
            booking.setActiveFlag(null);
            booking.setCancelledAt(now);
            bookingRepository.save(booking);

            entitlementService.restore(booking.getId(), "Session cancelled: " + reason);

            outboxService.append("booking", booking.getId(), "booking.cancelled", Map.of(
                    "bookingId", booking.getId(),
                    "memberId", booking.getMemberId(),
                    "sessionId", sessionId,
                    "cancelledBy", "ADMIN"
            ));
        }
        return confirmed.size();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AttendeeSummary> getAttendeesForSession(Long sessionId) {
        List<Booking> bookings = bookingRepository.findBySessionId(sessionId);
        return bookings.stream()
                .map(booking -> {
                    String firstName = userRepository.findById(booking.getMemberId())
                            .map(u -> {
                                String[] parts = u.getFullName().trim().split("\\s+");
                                return parts.length > 0 ? parts[0] : u.getFullName();
                            })
                            .orElse("Member");
                    return new AttendeeSummary(booking.getId(), firstName, booking.getStatus().name());
                })
                .toList();
    }

    public record BookingResult(int status, Object body, boolean cached) {}
}
