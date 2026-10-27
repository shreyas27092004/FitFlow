package com.fitflow.user;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.booking.BookingCancellationStub;
import com.fitflow.common.error.ApiException;
import com.fitflow.notification.entity.OutboxEvent;
import com.fitflow.notification.repository.OutboxEventRepository;
import com.fitflow.user.dto.DeleteAccountRequest;
import com.fitflow.user.dto.NotificationPrefsDto;
import com.fitflow.user.dto.UpdateProfileRequest;
import com.fitflow.user.dto.UserDataExportResponse;
import com.fitflow.user.dto.UserProfileResponse;
import com.fitflow.user.entity.User;
import com.fitflow.user.entity.UserRole;
import com.fitflow.user.repository.UserRepository;
import com.fitflow.user.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RefreshTokenRevoker refreshTokenRevoker;
    private final BookingCancellationStub bookingCancellationStub;
    private final OutboxEventRepository outboxEventRepository;
    private final PasswordEncoder passwordEncoder;
    private final AgeService ageService;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));

        List<String> roles = loadRoles(userId);
        NotificationPrefsDto prefs = parseNotificationPrefs(user.getNotificationPrefs());

        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                user.getDateOfBirth(),
                roles,
                user.getStatus().name(),
                user.getCreatedAt(),
                prefs,
                user.getVersion()
        );
    }

    @Transactional
    public UserProfileResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));

        if (!user.getVersion().equals(request.version())) {
            throw ApiException.optimisticLock("User profile was modified concurrently");
        }

        if (request.dateOfBirth() != null) {
            if (user.getDateOfBirth() != null && !user.getDateOfBirth().equals(request.dateOfBirth())) {
                throw ApiException.validationFailed("Date of birth cannot be updated once set; contact an administrator");
            }
            if (user.getDateOfBirth() == null) {
                ageService.validateRegistrationAge(request.dateOfBirth());
                user.setDateOfBirth(request.dateOfBirth());
            }
        }

        if (request.fullName() != null && !request.fullName().isBlank()) {
            user.setFullName(request.fullName().trim());
        }

        if (request.phone() != null) {
            user.setPhone(request.phone().isBlank() ? null : request.phone().trim());
        }

        if (request.notificationPrefs() != null) {
            try {
                user.setNotificationPrefs(objectMapper.writeValueAsString(request.notificationPrefs()));
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize notification preferences", e);
                throw ApiException.internalError("Failed to serialize notification preferences");
            }
        }

        User savedUser = userRepository.save(user);
        log.info("Profile updated for user id {}", savedUser.getId());

        List<String> roles = loadRoles(userId);
        NotificationPrefsDto prefs = parseNotificationPrefs(savedUser.getNotificationPrefs());

        return new UserProfileResponse(
                savedUser.getId(),
                savedUser.getEmail(),
                savedUser.getFullName(),
                savedUser.getPhone(),
                savedUser.getDateOfBirth(),
                roles,
                savedUser.getStatus().name(),
                savedUser.getCreatedAt(),
                prefs,
                savedUser.getVersion()
        );
    }

    @Transactional(readOnly = true)
    public UserDataExportResponse exportUserData(Long userId) {
        UserProfileResponse profile = getProfile(userId);
        return new UserDataExportResponse(
                profile,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList()
        );
    }

    @Transactional
    public void requestDelete(Long userId, DeleteAccountRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw ApiException.unauthenticated("Invalid password");
        }

        Instant now = clock.instant();
        user.setStatus(User.Status.DELETED);
        user.setDeletedAt(now);
        user.setEmail(String.format("deleted-%d@deleted.invalid", user.getId()));
        user.setFullName(null);
        user.setPhone(null);
        userRepository.save(user);

        refreshTokenRevoker.revokeAllForUser(userId);
        bookingCancellationStub.cancelFutureBookingsForUser(userId);

        String payload = String.format("{\"userId\":%d}", userId);
        OutboxEvent event = OutboxEvent.builder()
                .aggregateType("USER")
                .aggregateId(userId)
                .eventType("user.deletion_requested")
                .payload(payload)
                .status(OutboxEvent.Status.PENDING)
                .attempts(0)
                .nextAttemptAt(now)
                .createdAt(now)
                .build();
        outboxEventRepository.save(event);

        log.info("Account deletion executed and outbox event emitted for user id {}", userId);
    }

    private List<String> loadRoles(Long userId) {
        List<UserRole> roles = userRoleRepository.findByIdUserId(userId);
        if (roles == null || roles.isEmpty()) {
            return Collections.emptyList();
        }
        return roles.stream().map(r -> r.getId().getRole().name()).toList();
    }

    private NotificationPrefsDto parseNotificationPrefs(String json) {
        if (json == null || json.isBlank()) {
            return NotificationPrefsDto.defaultPrefs();
        }
        try {
            return objectMapper.readValue(json, NotificationPrefsDto.class);
        } catch (Exception e) {
            log.warn("Failed to parse notification preferences from JSON, falling back to defaults: {}", json);
            return NotificationPrefsDto.defaultPrefs();
        }
    }
}
