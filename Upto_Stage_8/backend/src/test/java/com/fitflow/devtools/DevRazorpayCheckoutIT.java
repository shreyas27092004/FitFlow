package com.fitflow.devtools;

import com.fitflow.payment.entity.Payment;
import com.fitflow.payment.gateway.razorpay.RazorpayClient;
import com.fitflow.payment.model.PaymentStatus;
import com.fitflow.payment.repository.PaymentRepository;
import com.fitflow.security.TokenService;
import com.fitflow.user.entity.User;
import com.fitflow.user.repository.UserRepository;
import com.fitflow.user.repository.UserRoleRepository;
import com.fitflow.user.entity.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class DevRazorpayCheckoutIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RazorpayClient razorpayClient;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private TokenService tokenService;

    private User owner;
    private User otherMember;
    private String ownerToken;
    private String otherToken;

    @BeforeEach
    void setUp() {
        razorpayClient.setCredentials("rzp_test_ScYoa7HPECrM5A", "FAKEsecret1234567890abcd", "whsec", null);

        owner = userRepository.findByEmail("dev_checkout_owner@example.com")
                .orElseGet(() -> userRepository.save(User.builder()
                        .email("dev_checkout_owner@example.com")
                        .passwordHash("hash")
                        .fullName("Checkout Owner")
                        .phone("+919999988888")
                        .status(User.Status.ACTIVE)
                        .build()));
        userRoleRepository.save(new UserRole(new UserRole.UserRoleId(owner.getId(), UserRole.Role.MEMBER)));
        ownerToken = tokenService.generateAccessToken(owner.getId(), owner.getEmail(), List.of("MEMBER"), List.of());

        otherMember = userRepository.findByEmail("other_checkout_user@example.com")
                .orElseGet(() -> userRepository.save(User.builder()
                        .email("other_checkout_user@example.com")
                        .passwordHash("hash")
                        .fullName("Other Member")
                        .status(User.Status.ACTIVE)
                        .build()));
        userRoleRepository.save(new UserRole(new UserRole.UserRoleId(otherMember.getId(), UserRole.Role.MEMBER)));
        otherToken = tokenService.generateAccessToken(otherMember.getId(), otherMember.getEmail(), List.of("MEMBER"), List.of());
    }

    @AfterEach
    void tearDown() {
        razorpayClient.deleteCredentials();
    }

    @Test
    @DisplayName("GET /dev/razorpay-checkout: returns standalone HTML page with Razorpay checkout script")
    void testGetCheckoutPage_returnsHtml() throws Exception {
        mockMvc.perform(get("/dev/razorpay-checkout?paymentId=42"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("checkout.razorpay.com/v1/checkout.js")))
                .andExpect(content().string(containsString("FitFlow Razorpay Dev Checkout")));
    }

    @Test
    @DisplayName("GET /api/v1/payments/{paymentId}/client-payload: owner receives 200 with client payload")
    void testGetClientPayload_ownerSuccess() throws Exception {
        Payment payment = paymentRepository.save(Payment.builder()
                .memberId(owner.getId())
                .amountPaise(89900L)
                .status(PaymentStatus.PENDING)
                .gateway("RAZORPAY")
                .gatewayOrderId("order_CLIENT_123")
                .currency("INR")
                .idempotencyKey("idemp-" + UUID.randomUUID())
                .createdAt(Instant.now())
                .build());

        mockMvc.perform(get("/api/v1/payments/" + payment.getId() + "/client-payload")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keyId", is("rzp_test_ScYoa7HPECrM5A")))
                .andExpect(jsonPath("$.orderId", is("order_CLIENT_123")))
                .andExpect(jsonPath("$.amount", is(89900)))
                .andExpect(jsonPath("$.currency", is("INR")))
                .andExpect(jsonPath("$.name", is("FitFlow")))
                .andExpect(jsonPath("$.prefill.email", is(owner.getEmail())))
                .andExpect(jsonPath("$.prefill.contact", is("+919999988888")));
    }

    @Test
    @DisplayName("GET /api/v1/payments/{paymentId}/client-payload: non-owner receives 404 Not Found")
    void testGetClientPayload_nonOwner404() throws Exception {
        Payment payment = paymentRepository.save(Payment.builder()
                .memberId(owner.getId())
                .amountPaise(89900L)
                .status(PaymentStatus.PENDING)
                .gateway("RAZORPAY")
                .gatewayOrderId("order_CLIENT_123")
                .currency("INR")
                .idempotencyKey("idemp-" + UUID.randomUUID())
                .createdAt(Instant.now())
                .build());

        mockMvc.perform(get("/api/v1/payments/" + payment.getId() + "/client-payload")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/v1/payments/{paymentId}/client-payload: payment not in PENDING status returns 409 Conflict")
    void testGetClientPayload_nonPending409() throws Exception {
        Payment payment = paymentRepository.save(Payment.builder()
                .memberId(owner.getId())
                .amountPaise(89900L)
                .status(PaymentStatus.SUCCEEDED)
                .gateway("RAZORPAY")
                .gatewayOrderId("order_CLIENT_123")
                .currency("INR")
                .idempotencyKey("idemp-" + UUID.randomUUID())
                .createdAt(Instant.now())
                .build());

        mockMvc.perform(get("/api/v1/payments/" + payment.getId() + "/client-payload")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("expected PENDING")));
    }

    @Test
    @DisplayName("GET /api/v1/payments/{paymentId}/client-payload: unauthenticated caller receives 401 Unauthorized")
    void testGetClientPayload_anonymous401() throws Exception {
        mockMvc.perform(get("/api/v1/payments/999/client-payload"))
                .andExpect(status().isUnauthorized());
    }
}
