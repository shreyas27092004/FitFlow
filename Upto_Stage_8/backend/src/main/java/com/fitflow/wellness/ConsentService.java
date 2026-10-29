package com.fitflow.wellness;

import com.fitflow.common.error.ApiException;
import com.fitflow.notification.OutboxService;
import com.fitflow.user.AgeService;
import com.fitflow.user.repository.UserRepository;
import com.fitflow.user.entity.User;
import com.fitflow.wellness.dto.ConsentInfoResponse;
import com.fitflow.wellness.dto.GrantConsentRequest;
import com.fitflow.wellness.dto.GrantedPurposeDto;
import com.fitflow.wellness.entity.HealthConsent;
import com.fitflow.wellness.model.ConsentPurpose;
import com.fitflow.wellness.model.HealthAccessAction;
import com.fitflow.wellness.repository.HealthConsentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConsentService {

    public static final String CONSENT_VERSION = "v1";

    private final HealthConsentRepository healthConsentRepository;
    private final UserRepository userRepository;
    private final AgeService ageService;
    private final OutboxService outboxService;
    private final HealthAccessService healthAccessService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public ConsentInfoResponse getConsentInfo(Long memberId) {
        String markdownText = loadConsentMarkdown();
        User user = userRepository.findById(memberId)
                .orElseThrow(() -> ApiException.notFound("User not found"));

        boolean eligible = ageService.is18Plus(user.getDateOfBirth());

        List<GrantedPurposeDto> granted = healthConsentRepository.findAllByMemberIdAndRevokedAtIsNull(memberId)
                .stream()
                .map(c -> {
                    try {
                        return new GrantedPurposeDto(ConsentPurpose.valueOf(c.getPurpose()), c.getGrantedAt());
                    } catch (IllegalArgumentException e) {
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull)
                .toList();

        return new ConsentInfoResponse(
                CONSENT_VERSION,
                markdownText,
                Arrays.asList(ConsentPurpose.values()),
                granted,
                eligible
        );
    }

    @Transactional
    public void grantConsent(Long memberId, GrantConsentRequest request) {
        User user = userRepository.findById(memberId)
                .orElseThrow(() -> ApiException.notFound("User not found"));

        if (!ageService.is18Plus(user.getDateOfBirth())) {
            throw ApiException.ageRestrictedForbidden("Age restricted: must be at least 18 years old to grant health consent");
        }

        if (!CONSENT_VERSION.equalsIgnoreCase(request.version())) {
            throw ApiException.conflict("Version mismatch: expected consent version " + CONSENT_VERSION);
        }

        for (ConsentPurpose purpose : request.purposes()) {
            boolean alreadyActive = healthConsentRepository.hasActiveConsent(memberId, purpose.name());
            if (!alreadyActive) {
                HealthConsent consent = HealthConsent.builder()
                        .memberId(memberId)
                        .consentVersion(CONSENT_VERSION)
                        .purpose(purpose.name())
                        .grantedAt(clock.instant())
                        .createdAt(clock.instant())
                        .build();
                healthConsentRepository.save(consent);
            }
        }

        outboxService.append(
                "MEMBER",
                memberId,
                "consent.granted",
                Map.of(
                        "memberId", memberId,
                        "purposes", request.purposes().stream().map(Enum::name).toList()
                )
        );

        healthAccessService.logAccess(memberId, memberId, HealthAccessAction.WRITE, "CONSENT_GRANT");
    }

    @Transactional
    public void revokeConsent(Long memberId, ConsentPurpose purpose) {
        Optional<HealthConsent> activeConsent = healthConsentRepository
                .findFirstByMemberIdAndPurposeAndRevokedAtIsNullOrderByGrantedAtDesc(memberId, purpose.name());

        if (activeConsent.isPresent()) {
            HealthConsent consent = activeConsent.get();
            consent.setRevokedAt(clock.instant());
            healthConsentRepository.save(consent);

            outboxService.append(
                    "MEMBER",
                    memberId,
                    "consent.revoked",
                    Map.of(
                            "memberId", memberId,
                            "purpose", purpose.name()
                    )
            );

            healthAccessService.logAccess(memberId, memberId, HealthAccessAction.WRITE, "CONSENT_REVOKE");
        }
    }

    @Transactional(readOnly = true)
    public void checkConsent(Long memberId, ConsentPurpose purpose) {
        if (!hasConsent(memberId, purpose)) {
            throw ApiException.consentRequired("Explicit consent required for purpose: " + purpose.name());
        }
    }

    @Transactional(readOnly = true)
    public boolean hasConsent(Long memberId, ConsentPurpose purpose) {
        return healthConsentRepository.hasActiveConsent(memberId, purpose.name());
    }

    private String loadConsentMarkdown() {
        try {
            ClassPathResource resource = new ClassPathResource("consent/v1.md");
            try (InputStream is = resource.getInputStream()) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            log.error("Failed to read consent/v1.md", e);
            throw ApiException.internalError("Failed to load consent text");
        }
    }
}
