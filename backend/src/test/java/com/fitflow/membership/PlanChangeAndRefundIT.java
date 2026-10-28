package com.fitflow.membership;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.membership.dto.ChangeMembershipRequest;
import com.fitflow.membership.dto.ChangePreviewRequest;
import com.fitflow.membership.dto.ChangePreviewResponse;
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
import com.fitflow.payment.PaymentService;
import com.fitflow.payment.dto.ConfirmPaymentRequest;
import com.fitflow.payment.dto.PaymentIntentResponse;
import com.fitflow.payment.entity.Payment;
import com.fitflow.payment.entity.Refund;
import com.fitflow.payment.model.PaymentStatus;
import com.fitflow.payment.model.RefundStatus;
import com.fitflow.payment.repository.PaymentRepository;
import com.fitflow.payment.repository.RefundAllocationRepository;
import com.fitflow.payment.repository.RefundRepository;
import com.fitflow.security.TokenService;
import com.fitflow.settings.entity.AppSetting;
import com.fitflow.settings.repository.AppSettingRepository;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
public class PlanChangeAndRefundIT {

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
    private MembershipChangeRepository membershipChangeRepository;

    @Autowired
    private PendingMembershipChangeRepository pendingChangeRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private RefundRepository refundRepository;

    @Autowired
    private RefundAllocationRepository refundAllocationRepository;

    @Autowired
    private com.fitflow.payment.repository.InvoiceRepository invoiceRepository;

    @Autowired
    private AppSettingRepository appSettingRepository;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private MembershipService membershipService;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private Clock clock;

    private User member;
    private String memberToken;
    private User admin1;
    private String admin1Token;
    private User admin2;
    private String admin2Token;

    private MembershipPlan basicPlan;
    private MembershipPlan proPlan;
    private MembershipPlan elitePlan;

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

        member = userRepository.save(User.builder()
                .email("member.refund." + stamp + "@test.com")
                .passwordHash("hashed")
                .fullName("Refund Test Member")
                .dateOfBirth(LocalDate.of(1993, 3, 3))
                .status(User.Status.ACTIVE)
                .build());
        userRoleRepository.save(new UserRole(new UserRole.UserRoleId(member.getId(), UserRole.Role.MEMBER)));
        memberToken = tokenService.generateAccessToken(member.getId(), member.getEmail(), List.of("MEMBER"), List.of());

        admin1 = userRepository.save(User.builder()
                .email("admin1.refund." + stamp + "@test.com")
                .passwordHash("hashed")
                .fullName("Admin One")
                .dateOfBirth(LocalDate.of(1980, 1, 1))
                .status(User.Status.ACTIVE)
                .build());
        userRoleRepository.save(new UserRole(new UserRole.UserRoleId(admin1.getId(), UserRole.Role.ADMIN)));
        admin1Token = tokenService.generateAccessToken(admin1.getId(), admin1.getEmail(), List.of("ADMIN"), List.of("REFUND_APPROVE"));

        admin2 = userRepository.save(User.builder()
                .email("admin2.refund." + stamp + "@test.com")
                .passwordHash("hashed")
                .fullName("Admin Two")
                .dateOfBirth(LocalDate.of(1982, 2, 2))
                .status(User.Status.ACTIVE)
                .build());
        userRoleRepository.save(new UserRole(new UserRole.UserRoleId(admin2.getId(), UserRole.Role.ADMIN)));
        admin2Token = tokenService.generateAccessToken(admin2.getId(), admin2.getEmail(), List.of("ADMIN"), List.of("REFUND_APPROVE"));

        membershipService.assignFreeMembershipIfMissing(member.getId());

        basicPlan = planRepository.findByCode("BASIC").orElseThrow();
        proPlan = planRepository.findByCode("PRO").orElseThrow();
        elitePlan = planRepository.findByCode("ELITE").orElseThrow();
    }

    @Test
    @DisplayName("Plan Upgrade: Upgrade from BASIC to PRO requires payment, preview matches, confirm activates PRO")
    void testUpgradeFromBasicToPro() throws Exception {
        // 1. Subscribe to BASIC and confirm
        PaymentIntentResponse subIntent = membershipService.subscribe(
                member.getId(), UUID.randomUUID().toString(), new SubscribeRequest("BASIC")
        );
        paymentService.confirmPayment(subIntent.paymentId(), member.getId(), Map.of("paymentMethodToken", "tok_ok"));

        // 2. Change preview to PRO
        ChangePreviewRequest previewReq = new ChangePreviewRequest("PRO", null);
        MvcResult previewResult = mockMvc.perform(post("/api/v1/memberships/change-preview")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(previewReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetPlan", is("PRO")))
                .andExpect(jsonPath("$.amountDuePaise", notNullValue()))
                .andReturn();

        ChangePreviewResponse preview = objectMapper.readValue(
                previewResult.getResponse().getContentAsString(), ChangePreviewResponse.class
        );
        assertTrue(preview.amountDuePaise() > 0);

        // 3. Initiate change with expected amount due
        ChangeMembershipRequest changeReq = new ChangeMembershipRequest("PRO", preview.amountDuePaise());
        MvcResult changeResult = mockMvc.perform(post("/api/v1/memberships/change")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changeReq)))
                .andExpect(status().isAccepted())
                .andReturn();

        PaymentIntentResponse changeIntent = objectMapper.readValue(
                changeResult.getResponse().getContentAsString(), PaymentIntentResponse.class
        );

        // Confirm change payment
        mockMvc.perform(post("/api/v1/payments/" + changeIntent.paymentId() + "/confirm")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ConfirmPaymentRequest(Map.of("paymentMethodToken", "tok_ok")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("SUCCEEDED")));

        // Verify old BASIC membership is CANCELLED and PRO is ACTIVE
        assertEquals(0, membershipRepository.countByPlanIdAndStatus(basicPlan.getId(), MembershipStatus.ACTIVE));
        assertEquals(1, membershipRepository.countByPlanIdAndStatus(proPlan.getId(), MembershipStatus.ACTIVE));
    }

    @Test
    @DisplayName("Stale Preview: Change request with wrong expectedAmountDuePaise returns 409 with new preview numbers")
    void testStalePreviewRejectedWith409() throws Exception {
        // Subscribe to BASIC
        PaymentIntentResponse subIntent = membershipService.subscribe(
                member.getId(), UUID.randomUUID().toString(), new SubscribeRequest("BASIC")
        );
        paymentService.confirmPayment(subIntent.paymentId(), member.getId(), Map.of("paymentMethodToken", "tok_ok"));

        // Change request with bogus expectedAmountDuePaise
        ChangeMembershipRequest badReq = new ChangeMembershipRequest("PRO", 12345L);

        mockMvc.perform(post("/api/v1/memberships/change")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.newPreview", notNullValue()))
                .andExpect(jsonPath("$.newPreview.targetPlan", is("PRO")));
    }

    @Test
    @DisplayName("Cancel Membership: Calculates proration credit, creates refund, ends paid membership")
    void testCancelMembershipCreatesRefund() throws Exception {
        // Subscribe to PRO and confirm
        PaymentIntentResponse subIntent = membershipService.subscribe(
                member.getId(), UUID.randomUUID().toString(), new SubscribeRequest("PRO")
        );
        paymentService.confirmPayment(subIntent.paymentId(), member.getId(), Map.of("paymentMethodToken", "tok_ok"));

        mockMvc.perform(post("/api/v1/memberships/cancel")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CANCELLED")))
                .andExpect(jsonPath("$.refundPaise", notNullValue()));

        // Paid membership is now CANCELLED
        assertEquals(0, membershipRepository.countByPlanIdAndStatus(proPlan.getId(), MembershipStatus.ACTIVE));

        // Refund record exists
        List<Refund> refunds = refundRepository.findAll();
        assertFalse(refunds.isEmpty());
        assertTrue(refunds.get(0).getAmountPaise() > 0);
    }

    @Test
    @DisplayName("Two-Person Rule: Admin who requested/triggered refund cannot approve own refund")
    void testRefundTwoPersonApprovalRule() throws Exception {
        // Set auto-approve limit to 0 so every refund requires manual approval
        appSettingRepository.save(AppSetting.builder()
                .name("refund.auto-approve-limit-paise")
                .value("0")
                .createdAt(clock.instant())
                .build());

        // Subscribe and cancel to trigger refund
        PaymentIntentResponse subIntent = membershipService.subscribe(
                member.getId(), UUID.randomUUID().toString(), new SubscribeRequest("BASIC")
        );
        paymentService.confirmPayment(subIntent.paymentId(), member.getId(), Map.of("paymentMethodToken", "tok_ok"));

        membershipService.cancelMembership(member.getId(), UUID.randomUUID().toString());

        List<Refund> refunds = refundRepository.findAll();
        assertFalse(refunds.isEmpty());
        Refund refund = refunds.get(0);
        assertEquals(RefundStatus.PENDING_APPROVAL, refund.getStatus());

        // Simulate admin 1 requested it
        refund.setApprovedBy(admin1.getId());
        refundRepository.save(refund);

        // Admin 1 attempts to approve own refund -> 409 CONFLICT
        mockMvc.perform(post("/api/v1/admin/refunds/" + refund.getId() + "/approve")
                        .header("Authorization", "Bearer " + admin1Token))
                .andExpect(status().isConflict());

        // Admin 2 approves -> 200 OK
        mockMvc.perform(post("/api/v1/admin/refunds/" + refund.getId() + "/approve")
                        .header("Authorization", "Bearer " + admin2Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("PAID")));
    }

    @Test
    @DisplayName("Refund Allocation: Multi-payment refund splits across multiple payments newest first")
    void testMultiPaymentRefundAllocationNewestFirst() {
        Instant now = clock.instant();

        // Payment 1 (Older): 10,000 paise
        Payment p1 = paymentRepository.save(Payment.builder()
                .memberId(member.getId())
                .amountPaise(10000L)
                .currency("INR")
                .status(PaymentStatus.SUCCEEDED)
                .gateway("MOCK")
                .gatewayOrderId("mock_ord_1")
                .gatewayPaymentId("mock_pay_1")
                .idempotencyKey("idem-p1-" + UUID.randomUUID())
                .createdAt(now.minus(Duration.ofDays(10)))
                .build());

        // Payment 2 (Newer): 15,000 paise
        Payment p2 = paymentRepository.save(Payment.builder()
                .memberId(member.getId())
                .amountPaise(15000L)
                .currency("INR")
                .status(PaymentStatus.SUCCEEDED)
                .gateway("MOCK")
                .gatewayOrderId("mock_ord_2")
                .gatewayPaymentId("mock_pay_2")
                .idempotencyKey("idem-p2-" + UUID.randomUUID())
                .createdAt(now.minus(Duration.ofDays(2)))
                .build());

        // Request refund of 20,000 paise
        Refund refund = paymentService.createAndExecuteRefund(member.getId(), 20000L, UUID.randomUUID().toString(), null);

        assertNotNull(refund);
        assertEquals(20000L, refund.getAmountPaise());

        var allocations = refundAllocationRepository.findByIdRefundId(refund.getId());
        assertEquals(2, allocations.size());

        // P2 (newer) allocated 15,000 paise
        var allocP2 = allocations.stream().filter(a -> a.getId().getPaymentId().equals(p2.getId())).findFirst().orElseThrow();
        assertEquals(15000L, allocP2.getAmountPaise());

        // P1 (older) allocated 5,000 paise
        var allocP1 = allocations.stream().filter(a -> a.getId().getPaymentId().equals(p1.getId())).findFirst().orElseThrow();
        assertEquals(5000L, allocP1.getAmountPaise());
    }

    @Test
    @DisplayName("Cash Cap: Proration credit cannot exceed total refundable cash remaining in payment chain")
    void testCashCapWhenCreditExceedsTotalCash() {
        // Total cash in chain is 0 (FREE plan)
        ChangePreviewResponse preview = membershipService.changePreview(
                member.getId(), new ChangePreviewRequest("BASIC", null)
        );
        assertEquals(0L, preview.creditPaise());
        assertEquals(49900L, preview.amountDuePaise());
    }
}
