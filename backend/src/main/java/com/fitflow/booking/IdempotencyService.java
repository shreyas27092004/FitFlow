package com.fitflow.booking;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.booking.entity.IdempotencyRecord;
import com.fitflow.booking.repository.IdempotencyRecordRepository;
import com.fitflow.common.error.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyService {

    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public void validateKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw ApiException.badRequest("Idempotency-Key header is required");
        }
        String trimmed = idempotencyKey.trim();
        if (trimmed.length() < 8 || trimmed.length() > 64) {
            throw ApiException.badRequest("Idempotency-Key header length must be between 8 and 64 characters");
        }
    }

    public String computeHash(Object requestBody) {
        try {
            String json = objectMapper.writeValueAsString(requestBody);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(json.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to compute request hash", e);
        }
    }

    @Transactional(readOnly = true)
    public Optional<CachedIdempotentResponse> check(Long userId, String idempotencyKey, String requestHash) {
        return idempotencyRecordRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                .map(record -> {
                    if (!record.getRequestHash().equals(requestHash)) {
                        log.warn("Idempotency mismatch for userId={}, key={}", userId, idempotencyKey);
                        throw ApiException.idempotencyReplayMismatch("Idempotency key was previously used with a different request payload");
                    }
                    return new CachedIdempotentResponse(record.getResponseStatus(), record.getResponsePayload());
                });
    }

    @Transactional
    public void record(Long userId, String idempotencyKey, String requestHash, int status, Object responseBody) {
        try {
            String jsonPayload = responseBody instanceof String str ? str : objectMapper.writeValueAsString(responseBody);
            IdempotencyRecord record = new IdempotencyRecord(
                    userId,
                    idempotencyKey,
                    requestHash,
                    status,
                    jsonPayload,
                    clock.instant()
            );
            idempotencyRecordRepository.save(record);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize idempotent response body", e);
        }
    }

    public record CachedIdempotentResponse(int status, String payload) {}
}
