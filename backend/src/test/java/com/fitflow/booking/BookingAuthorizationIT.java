package com.fitflow.booking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.booking.dto.AdminCancelBookingRequest;
import com.fitflow.booking.dto.AttendanceRecordDto;
import com.fitflow.booking.dto.CompleteSessionRequest;
import com.fitflow.booking.dto.CreateBookingRequest;
import com.fitflow.booking.dto.MarkAttendanceRequest;
import com.fitflow.booking.model.AttendanceStatus;
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

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class BookingAuthorizationIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private Clock clock;

    private String memberToken;
    private String trainerToken;

    @BeforeEach
    void setUp() {
        long stamp = clock.millis();

        User member = userRepository.save(User.builder()
                .email("auth.member." + stamp + "@test.com")
                .passwordHash("hashed")
                .fullName("Auth Member")
                .dateOfBirth(LocalDate.of(1995, 1, 1))
                .status(User.Status.ACTIVE)
                .build());
        userRoleRepository.save(new UserRole(new UserRole.UserRoleId(member.getId(), UserRole.Role.MEMBER)));
        memberToken = tokenService.generateAccessToken(member.getId(), member.getEmail(), List.of("MEMBER"), List.of());

        User trainer = userRepository.save(User.builder()
                .email("auth.trainer." + stamp + "@test.com")
                .passwordHash("hashed")
                .fullName("Auth Trainer")
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .status(User.Status.ACTIVE)
                .build());
        userRoleRepository.save(new UserRole(new UserRole.UserRoleId(trainer.getId(), UserRole.Role.TRAINER)));
        trainerToken = tokenService.generateAccessToken(trainer.getId(), trainer.getEmail(), List.of("TRAINER"), List.of());
    }

    @Test
    @DisplayName("Anonymous user cannot access /api/v1/bookings -> 401 UNAUTHENTICATED")
    void testAnonymousBookingRejected() throws Exception {
        mockMvc.perform(post("/api/v1/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateBookingRequest(1L))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Anonymous user cannot access /api/v1/me/bookings -> 401 UNAUTHENTICATED")
    void testAnonymousGetMyBookingsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/me/bookings"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Anonymous user cannot access /api/v1/waitlist/{sessionId} -> 401 UNAUTHENTICATED")
    void testAnonymousLeaveWaitlistRejected() throws Exception {
        mockMvc.perform(delete("/api/v1/waitlist/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Member cannot call /api/v1/admin/bookings/{id}/cancel -> 403 FORBIDDEN")
    void testMemberCannotCallAdminCancelBooking() throws Exception {
        mockMvc.perform(post("/api/v1/admin/bookings/1/cancel")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AdminCancelBookingRequest("test"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Trainer cannot call /api/v1/admin/bookings/{id}/cancel -> 403 FORBIDDEN")
    void testTrainerCannotCallAdminCancelBooking() throws Exception {
        mockMvc.perform(post("/api/v1/admin/bookings/1/cancel")
                        .header("Authorization", "Bearer " + trainerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AdminCancelBookingRequest("test"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Member cannot mark attendance -> 403 FORBIDDEN")
    void testMemberCannotMarkAttendance() throws Exception {
        MarkAttendanceRequest req = new MarkAttendanceRequest(List.of(new AttendanceRecordDto(1L, AttendanceStatus.ATTENDED)));
        mockMvc.perform(post("/api/v1/trainer/sessions/1/attendance")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Member cannot complete session -> 403 FORBIDDEN")
    void testMemberCannotCompleteSession() throws Exception {
        mockMvc.perform(post("/api/v1/trainer/sessions/1/complete")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CompleteSessionRequest(true))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Trainer cannot book session with MEMBER endpoint -> 403 FORBIDDEN")
    void testTrainerCannotCreateBookingAsMember() throws Exception {
        mockMvc.perform(post("/api/v1/bookings")
                        .header("Authorization", "Bearer " + trainerToken)
                        .header("Idempotency-Key", "idemp-trainer-forbidden")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateBookingRequest(1L))))
                .andExpect(status().isForbidden());
    }
}
