package com.fitflow.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.membership.MembershipService;
import com.fitflow.membership.dto.SubscribeRequest;
import com.fitflow.membership.entity.Membership;
import com.fitflow.membership.entity.MembershipPlan;
import com.fitflow.membership.entity.PendingMembershipChange;
import com.fitflow.membership.model.MembershipStatus;
import com.fitflow.membership.model.PendingChangeStatus;
import com.fitflow.membership.repository.MembershipChangeRepository;
import com.fitflow.membership.repository.MembershipPlanRepository;
import com.fitflow.membership.repository.MembershipRepository;
import com.fitflow.membership.repository.PendingMembershipChangeRepository;
import com.fitflow.notification.repository.OutboxEventRepository;
import com.fitflow.payment.dto.ConfirmPaymentRequest;
import com.fitflow.payment.dto.PaymentIntentResponse;
import com.fitflow.payment.entity.Invoice;
import com.fitflow.payment.entity.Payment;
import com.fitflow.payment.gateway.PaymentGateway.ConfirmResult;
import com.fitflow.payment.model.PaymentStatus;
import com.fitflow.payment.repository.InvoiceRepository;
import com.fitflow.payment.repository.PaymentRepository;
import com.fitflow.payment.repository.RefundAllocationRepository;
import com.fitflow.payment.repository.RefundRepository;
import com.fitflow.security.TokenService;
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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class TwoPhaseSubscriptionIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private MembershipPlanRepository planRepository;

    @Autowired
    private MembershipRepository membershipRepository;

    @Autowired
    private PendingMembershipChangeRepository pendingChangeRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private RefundAllocationRepository refundAllocationRepository;

    @Autowired
    private RefundRepository refundRepository;

    @Autowired
    private MembershipChangeRepository membershipChangeRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentFinalizer paymentFinalizer;

    @Autowired
    private MembershipService membershipService;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private Clock clock;

    private User member1;
    private String token1;
    private User member2;
    private String token2;
    private MembershipPlan basicPlan;

    @BeforeEach
    void setUp() {
        refundAllocationRepository.deleteAll();
        refundRepository.deleteAll();
        pendingChangeRepository.deleteAll();
        membershipChangeRepository.deleteAll();
        invoiceRepository.deleteAll();
        paymentRepository.deleteAll();
        membershipRepository.deleteAll();

        long stamp = clock.millis();

        member1 = userRepository.save(User.builder()
                .email("member1." + stamp + "@test.com")
                .passwordHash("hashed")
                .fullName("Member One")
                .dateOfBirth(LocalDate.of(1995, 1, 1))
                .status(User.Status.ACTIVE)
                .build());
        userRoleRepository.save(new UserRole(new UserRole.UserRoleId(member1.getId(), UserRole.Role.MEMBER)));
        token1 = tokenService.generateAccessToken(member1.getId(), member1.getEmail(), List.of("MEMBER"), List.of());

        member2 = userRepository.save(User.builder()
                .email("member2." + stamp + "@test.com")
                .passwordHash("hashed")
                .fullName("Member Two")
                .dateOfBirth(LocalDate.of(1996, 2, 2))
                .status(User.Status.ACTIVE)
                .build());
        userRoleRepository.save(new UserRole(new UserRole.UserRoleId(member2.getId(), UserRole.Role.MEMBER)));
        token2 = tokenService.generateAccessToken(member2.getId(), member2.getEmail(), List.of("MEMBER"), List.of());

        // Assign free tier to member 1 and member 2
        membershipService.assignFreeMembershipIfMissing(member1.getId());
        membershipService.assignFreeMembershipIfMissing(member2.getId());

        basicPlan = planRepository.findByCode("BASIC").orElseThrow();
    }

    @Test
    @DisplayName("Two-Phase Subscribe: Phase 1 returns 202 ACCEPTED with intent and leaves membership unchanged")
    void testSubscribePhase1LeavesMembershipUnchanged() throws Exception {
        SubscribeRequest request = new SubscribeRequest("BASIC");

        MvcResult result = mockMvc.perform(post("/api/v1/memberships/subscribe")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.paymentId", notNullValue()))
                .andExpect(jsonPath("$.status", is("REQUIRES_PAYMENT")))
                .andExpect(jsonPath("$.amountPaise", is(49900)))
                .andReturn();

        PaymentIntentResponse intent = objectMapper.readValue(
                result.getResponse().getContentAsString(), PaymentIntentResponse.class
        );

        // Verify database: Payment is PENDING
        Payment payment = paymentRepository.findById(intent.paymentId()).orElseThrow();
        assertEquals(PaymentStatus.PENDING, payment.getStatus());

        // Verify pending change is PENDING
        PendingMembershipChange pending = pendingChangeRepository.findByPaymentId(intent.paymentId()).orElseThrow();
        assertEquals(PendingChangeStatus.PENDING, pending.getStatus());

        // Verify membership remains unchanged: only FREE tier is ACTIVE
        List<Membership> activeMems = membershipRepository.findByMemberIdAndStatus(member1.getId(), MembershipStatus.ACTIVE);
        assertEquals(1, activeMems.size());
        assertEquals("FREE", planRepository.findById(activeMems.get(0).getPlanId()).get().getCode());
    }

    @Test
    @DisplayName("Two-Phase Subscribe: Phase 2 with tok_ok activates membership, creates invoice and emits events")
    void testSubscribePhase2WithTokOkActivatesMembership() throws Exception {
        SubscribeRequest request = new SubscribeRequest("BASIC");

        MvcResult subResult = mockMvc.perform(post("/api/v1/memberships/subscribe")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andReturn();

        PaymentIntentResponse intent = objectMapper.readValue(
                subResult.getResponse().getContentAsString(), PaymentIntentResponse.class
        );

        // Phase 2: Confirm with tok_ok
        ConfirmPaymentRequest confirmReq = new ConfirmPaymentRequest(Map.of("paymentMethodToken", "tok_ok"));

        mockMvc.perform(post("/api/v1/payments/" + intent.paymentId() + "/confirm")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("SUCCEEDED")))
                .andExpect(jsonPath("$.membershipChangeApplied", is(true)));

        // Verify Payment is SUCCEEDED
        Payment payment = paymentRepository.findById(intent.paymentId()).orElseThrow();
        assertEquals(PaymentStatus.SUCCEEDED, payment.getStatus());

        // Verify Invoice was created
        var invoiceOpt = invoiceRepository.findByPaymentId(intent.paymentId());
        assertTrue(invoiceOpt.isPresent());
        assertEquals(49900L, invoiceOpt.get().getTotalPaise());

        // Verify Active BASIC membership is now created
        List<Membership> activeMems = membershipRepository.findByMemberIdAndStatus(member1.getId(), MembershipStatus.ACTIVE);
        assertEquals(2, activeMems.size()); // 1 FREE + 1 BASIC
        boolean hasBasic = activeMems.stream()
                .anyMatch(m -> m.getPlanId().equals(basicPlan.getId()) && m.getSessionsTotal() == 8);
        assertTrue(hasBasic);

        // Verify Pending change is APPLIED
        PendingMembershipChange pending = pendingChangeRepository.findByPaymentId(intent.paymentId()).orElseThrow();
        assertEquals(PendingChangeStatus.APPLIED, pending.getStatus());
    }

    @Test
    @DisplayName("Two-Phase Subscribe: Duplicate confirm is completely idempotent")
    void testDuplicateConfirmIsIdempotent() throws Exception {
        PaymentIntentResponse intent = membershipService.subscribe(
                member1.getId(), UUID.randomUUID().toString(), new SubscribeRequest("BASIC")
        );

        ConfirmPaymentRequest confirmReq = new ConfirmPaymentRequest(Map.of("paymentMethodToken", "tok_ok"));

        // Call 1
        mockMvc.perform(post("/api/v1/payments/" + intent.paymentId() + "/confirm")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("SUCCEEDED")));

        // Call 2 (Duplicate)
        mockMvc.perform(post("/api/v1/payments/" + intent.paymentId() + "/confirm")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("SUCCEEDED")));

        // Exactly 1 invoice and exactly 1 BASIC membership exist
        assertTrue(invoiceRepository.findByPaymentId(intent.paymentId()).isPresent());
        assertEquals(1, membershipRepository.countByPlanIdAndStatus(basicPlan.getId(), MembershipStatus.ACTIVE));
    }

    @Test
    @DisplayName("Two-Phase Subscribe: Confirm with tok_fail marks payment and pending change FAILED, membership unchanged")
    void testConfirmWithTokFail() throws Exception {
        PaymentIntentResponse intent = membershipService.subscribe(
                member1.getId(), UUID.randomUUID().toString(), new SubscribeRequest("BASIC")
        );

        ConfirmPaymentRequest confirmReq = new ConfirmPaymentRequest(Map.of("paymentMethodToken", "tok_fail"));

        mockMvc.perform(post("/api/v1/payments/" + intent.paymentId() + "/confirm")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("FAILED")));

        Payment payment = paymentRepository.findById(intent.paymentId()).orElseThrow();
        assertEquals(PaymentStatus.FAILED, payment.getStatus());

        PendingMembershipChange pending = pendingChangeRepository.findByPaymentId(intent.paymentId()).orElseThrow();
        assertEquals(PendingChangeStatus.FAILED, pending.getStatus());

        assertEquals(0, membershipRepository.countByPlanIdAndStatus(basicPlan.getId(), MembershipStatus.ACTIVE));
    }

    @Test
    @DisplayName("Security: Member A cannot confirm or view Member B's payment (returns 404 NOT_FOUND)")
    void testOwnerIsolationOnPaymentConfirmation() throws Exception {
        PaymentIntentResponse intent = membershipService.subscribe(
                member1.getId(), UUID.randomUUID().toString(), new SubscribeRequest("BASIC")
        );

        ConfirmPaymentRequest confirmReq = new ConfirmPaymentRequest(Map.of("paymentMethodToken", "tok_ok"));

        // Member 2 attempts to confirm Member 1's payment
        mockMvc.perform(post("/api/v1/payments/" + intent.paymentId() + "/confirm")
                        .header("Authorization", "Bearer " + token2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmReq)))
                .andExpect(status().isNotFound());

        // Member 2 attempts to view Member 1's payment
        mockMvc.perform(get("/api/v1/payments/" + intent.paymentId())
                        .header("Authorization", "Bearer " + token2))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Concurrency: 20 parallel confirm requests result in exactly one payment finalization and one membership")
    void testConcurrentConfirmsResultInExactSingleFinalization() throws Exception {
        PaymentIntentResponse intent = membershipService.subscribe(
                member1.getId(), UUID.randomUUID().toString(), new SubscribeRequest("BASIC")
        );

        int threads = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Callable<ConfirmResult>> tasks = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            tasks.add(() -> {
                ConfirmResult res = new ConfirmResult(PaymentStatus.SUCCEEDED, "mock_pay_" + UUID.randomUUID(), null, 49900L);
                paymentFinalizer.finalizePayment(intent.paymentId(), res);
                return res;
            });
        }

        List<Future<ConfirmResult>> futures = executor.invokeAll(tasks);
        for (Future<ConfirmResult> f : futures) {
            assertNotNull(f.get());
        }
        executor.shutdown();

        // Exactly 1 invoice, 1 BASIC membership, 1 APPLIED pending change, exactly 1 payment.succeeded event
        assertTrue(invoiceRepository.findByPaymentId(intent.paymentId()).isPresent());
        assertEquals(1, membershipRepository.countByPlanIdAndStatus(basicPlan.getId(), MembershipStatus.ACTIVE));
        assertEquals(1, pendingChangeRepository.findAll().stream().filter(p -> p.getStatus() == PendingChangeStatus.APPLIED).count());
        assertEquals(1, outboxEventRepository.findAll().stream()
                .filter(e -> "payment.succeeded".equals(e.getEventType()) && intent.paymentId().equals(e.getAggregateId()))
                .count(), "Exactly one payment.succeeded outbox event must be emitted");
    }
}
