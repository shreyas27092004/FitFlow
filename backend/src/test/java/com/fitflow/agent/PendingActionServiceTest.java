package com.fitflow.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fitflow.agent.dto.PendingActionProposal;
import com.fitflow.agent.entity.PendingAction;
import com.fitflow.agent.repository.PendingActionRepository;
import com.fitflow.agent.service.PendingActionService;
import com.fitflow.agent.tools.dto.ProposeBookingArgs;
import com.fitflow.agent.tools.dto.ProposeMembershipChangeArgs;
import com.fitflow.booking.BookingService;
import com.fitflow.booking.dto.BookingResponse;
import com.fitflow.common.error.ApiException;
import com.fitflow.membership.MembershipService;
import com.fitflow.membership.dto.ChangePreviewResponse;
import com.fitflow.membership.dto.MemberMembershipSummaryResponse;
import com.fitflow.payment.dto.PaymentIntentResponse;
import com.fitflow.user.entity.User;
import com.fitflow.user.entity.UserRole;
import com.fitflow.user.repository.UserRepository;
import com.fitflow.user.repository.UserRoleRepository;
import com.fitflow.wellness.WellnessProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PendingActionServiceTest {

    private PendingActionRepository pendingActionRepository;
    private UserRepository userRepository;
    private UserRoleRepository userRoleRepository;
    private com.fitflow.user.repository.AdminPermissionRepository adminPermissionRepository;
    private BookingService bookingService;
    private MembershipService membershipService;
    private WellnessProfileService wellnessProfileService;
    private com.fitflow.agent.service.WeeklyPlanService weeklyPlanService;
    private com.fitflow.agent.service.AtRiskService atRiskService;
    private com.fitflow.fitnessclass.FitnessClassService fitnessClassService;
    private com.fitflow.session.ClassSessionService sessionService;
    private com.fitflow.agent.service.DemandService demandService;
    private com.fitflow.notification.OutboxService outboxService;
    private Clock fixedClock;
    private ObjectMapper objectMapper;

    private PendingActionService service;

    private final Instant baseTime = Instant.parse("2026-11-01T10:00:00Z");

    @BeforeEach
    void setUp() {
        pendingActionRepository = Mockito.mock(PendingActionRepository.class);
        userRepository = Mockito.mock(UserRepository.class);
        userRoleRepository = Mockito.mock(UserRoleRepository.class);
        adminPermissionRepository = Mockito.mock(com.fitflow.user.repository.AdminPermissionRepository.class);
        bookingService = Mockito.mock(BookingService.class);
        membershipService = Mockito.mock(MembershipService.class);
        wellnessProfileService = Mockito.mock(WellnessProfileService.class);
        weeklyPlanService = Mockito.mock(com.fitflow.agent.service.WeeklyPlanService.class);
        atRiskService = Mockito.mock(com.fitflow.agent.service.AtRiskService.class);
        fitnessClassService = Mockito.mock(com.fitflow.fitnessclass.FitnessClassService.class);
        sessionService = Mockito.mock(com.fitflow.session.ClassSessionService.class);
        demandService = Mockito.mock(com.fitflow.agent.service.DemandService.class);
        outboxService = Mockito.mock(com.fitflow.notification.OutboxService.class);

        fixedClock = Clock.fixed(baseTime, ZoneOffset.UTC);
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        service = new PendingActionService(
                pendingActionRepository,
                userRepository,
                userRoleRepository,
                adminPermissionRepository,
                bookingService,
                membershipService,
                wellnessProfileService,
                weeklyPlanService,
                atRiskService,
                fitnessClassService,
                sessionService,
                demandService,
                outboxService,
                fixedClock,
                objectMapper
        );
    }

    @Test
    @DisplayName("Propose creates pending action and executes nothing (booking count unchanged)")
    void testProposeCreatesPendingActionAndExecutesNothing() {
        when(pendingActionRepository.save(any(PendingAction.class))).thenAnswer(invocation -> {
            PendingAction action = invocation.getArgument(0);
            action.setId(100L);
            return action;
        });

        PendingActionProposal proposal = service.propose(
                1L, 42L, "propose_booking", new ProposeBookingArgs(501L), "Book Yoga Flow"
        );

        assertNotNull(proposal);
        assertEquals(100L, proposal.id());
        assertEquals("propose_booking", proposal.toolName());
        assertEquals(baseTime.plusSeconds(600), proposal.expiresAt());

        // Assert nothing executed
        verify(bookingService, never()).createBooking(any(), any(), any());
        verify(membershipService, never()).subscribe(any(), any(), any());
    }

    @Test
    @DisplayName("Confirm executes once with idempotency key")
    void testConfirmExecutesOnce() {
        PendingAction action = PendingAction.builder()
                .id(100L)
                .conversationId(1L)
                .userId(42L)
                .toolName("propose_booking")
                .argsJson("{\"sessionId\":501}")
                .summary("Book Yoga Flow")
                .status(PendingAction.Status.PENDING.name())
                .expiresAt(baseTime.plusSeconds(600))
                .createdAt(baseTime)
                .build();

        when(pendingActionRepository.findById(100L)).thenReturn(Optional.of(action));
        when(userRepository.findById(42L)).thenReturn(Optional.of(new User()));
        when(userRoleRepository.existsByIdUserIdAndIdRole(42L, UserRole.Role.MEMBER)).thenReturn(true);

        when(bookingService.createBooking(any(), eq("agent-100"), eq(42L)))
                .thenReturn(new BookingService.BookingResult(201, Map.of("bookingId", 999L), false));

        Map<String, Object> result = service.confirm(100L, 42L);

        assertEquals("CONFIRMED", result.get("status"));
        verify(bookingService, times(1)).createBooking(any(), eq("agent-100"), eq(42L));
        assertEquals(PendingAction.Status.CONFIRMED.name(), action.getStatus());
    }

    @Test
    @DisplayName("Double confirm is idempotent (returns stored result, booking called once)")
    void testDoubleConfirmIsIdempotent() {
        PendingAction action = PendingAction.builder()
                .id(100L)
                .conversationId(1L)
                .userId(42L)
                .toolName("propose_booking")
                .argsJson("{\"sessionId\":501}")
                .summary("Book Yoga Flow")
                .status(PendingAction.Status.CONFIRMED.name())
                .resultJson("{\"status\":\"CONFIRMED\",\"sessionId\":501}")
                .expiresAt(baseTime.plusSeconds(600))
                .createdAt(baseTime)
                .build();

        when(pendingActionRepository.findById(100L)).thenReturn(Optional.of(action));

        Map<String, Object> result = service.confirm(100L, 42L);

        assertEquals("CONFIRMED", result.get("status"));
        assertEquals(501, result.get("sessionId"));
        // Never called again because already CONFIRMED
        verify(bookingService, never()).createBooking(any(), any(), any());
    }

    @Test
    @DisplayName("Expired action is rejected with 409 conflict")
    void testExpiredActionRejectedWithConflict() {
        PendingAction action = PendingAction.builder()
                .id(100L)
                .conversationId(1L)
                .userId(42L)
                .toolName("propose_booking")
                .argsJson("{\"sessionId\":501}")
                .summary("Book Yoga Flow")
                .status(PendingAction.Status.PENDING.name())
                .expiresAt(baseTime.minusSeconds(10)) // in the past
                .createdAt(baseTime.minusSeconds(700))
                .build();

        when(pendingActionRepository.findById(100L)).thenReturn(Optional.of(action));

        ApiException ex = assertThrows(ApiException.class, () -> service.confirm(100L, 42L));
        assertEquals(409, ex.getStatus().value());
        assertTrue(ex.getMessage().contains("expired"));
        verify(bookingService, never()).createBooking(any(), any(), any());
    }

    @Test
    @DisplayName("IDOR Gate: Another user's action throws 404 (not found)")
    void testAnotherUsersActionThrowsNotFound() {
        PendingAction action = PendingAction.builder()
                .id(100L)
                .conversationId(1L)
                .userId(999L) // different user
                .toolName("propose_booking")
                .argsJson("{\"sessionId\":501}")
                .summary("Book Yoga Flow")
                .status(PendingAction.Status.PENDING.name())
                .expiresAt(baseTime.plusSeconds(600))
                .createdAt(baseTime)
                .build();

        when(pendingActionRepository.findById(100L)).thenReturn(Optional.of(action));

        ApiException ex = assertThrows(ApiException.class, () -> service.confirm(100L, 42L));
        assertEquals(404, ex.getStatus().value());
        verify(bookingService, never()).createBooking(any(), any(), any());
    }

    @Test
    @DisplayName("Role Revoked Gate: Permission revoked between propose and confirm -> refused")
    void testPermissionRevokedBetweenProposeAndConfirmRefused() {
        PendingAction action = PendingAction.builder()
                .id(100L)
                .conversationId(1L)
                .userId(42L)
                .toolName("propose_booking")
                .argsJson("{\"sessionId\":501}")
                .summary("Book Yoga Flow")
                .status(PendingAction.Status.PENDING.name())
                .expiresAt(baseTime.plusSeconds(600))
                .createdAt(baseTime)
                .build();

        when(pendingActionRepository.findById(100L)).thenReturn(Optional.of(action));
        when(userRepository.findById(42L)).thenReturn(Optional.of(new User()));
        // Member role revoked!
        when(userRoleRepository.existsByIdUserIdAndIdRole(42L, UserRole.Role.MEMBER)).thenReturn(false);

        ApiException ex = assertThrows(ApiException.class, () -> service.confirm(100L, 42L));
        assertEquals(403, ex.getStatus().value());
        assertTrue(ex.getMessage().contains("MEMBER"));
        verify(bookingService, never()).createBooking(any(), any(), any());
    }

    @Test
    @DisplayName("Membership change confirm creates payment intent and does not confirm payment")
    void testMembershipChangeCreatesPaymentIntentWithoutAutoCapture() {
        PendingAction action = PendingAction.builder()
                .id(200L)
                .conversationId(1L)
                .userId(42L)
                .toolName("propose_membership_change")
                .argsJson("{\"targetPlanCode\":\"PRO\",\"action\":\"UPGRADE\"}")
                .summary("Upgrade to FitFlow Pro")
                .status(PendingAction.Status.PENDING.name())
                .expiresAt(baseTime.plusSeconds(600))
                .createdAt(baseTime)
                .build();

        when(pendingActionRepository.findById(200L)).thenReturn(Optional.of(action));
        when(userRepository.findById(42L)).thenReturn(Optional.of(new User()));
        when(userRoleRepository.existsByIdUserIdAndIdRole(42L, UserRole.Role.MEMBER)).thenReturn(true);

        // User currently has no paid plan -> subscribe flow creates PaymentIntent
        when(membershipService.getMemberMembership(42L)).thenReturn(new MemberMembershipSummaryResponse(0, null));

        PaymentIntentResponse pi = new PaymentIntentResponse(
                301L, "REQUIRES_PAYMENT_METHOD", "STRIPE", "pi_test_123",
                499900L, "INR", Map.of("client_secret", "xyz"), baseTime.plusSeconds(3600)
        );
        when(membershipService.subscribe(eq(42L), eq("agent-200"), any())).thenReturn(pi);

        Map<String, Object> result = service.confirm(200L, 42L);

        assertNotNull(result);
        assertEquals("REQUIRES_PAYMENT_METHOD", result.get("status"));
        assertEquals("pi_test_123", result.get("gatewayOrderId"));

        // Verifies subscribe was called with idempotency key
        verify(membershipService, times(1)).subscribe(eq(42L), eq("agent-200"), any());
    }
}
