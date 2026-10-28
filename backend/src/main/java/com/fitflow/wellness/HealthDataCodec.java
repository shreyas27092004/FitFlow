package com.fitflow.wellness;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitflow.common.error.ApiException;
import com.fitflow.security.EncryptionService;
import com.fitflow.wellness.dto.FitnessProfile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class HealthDataCodec {

    private final EncryptionService encryptionService;
    private final ObjectMapper objectMapper;

    /**
     * Serializes FitnessProfile to JSON and encrypts it via AES-256-GCM.
     */
    public byte[] encode(FitnessProfile profile) {
        if (profile == null) {
            return null;
        }
        try {
            String json = objectMapper.writeValueAsString(profile);
            return encryptionService.encrypt(json.getBytes(StandardCharsets.UTF_8));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize fitness profile for encryption");
            throw ApiException.internalError("Failed to encode health profile");
        }
    }

    /**
     * Decrypts AES-256-GCM ciphertext bytes and deserializes to FitnessProfile.
     * Safely traps tampering or integrity failures without leaking ciphertext details.
     */
    public FitnessProfile decode(byte[] cipherBytes) {
        if (cipherBytes == null || cipherBytes.length == 0) {
            return null;
        }
        try {
            byte[] decryptedBytes = encryptionService.decrypt(cipherBytes);
            String json = new String(decryptedBytes, StandardCharsets.UTF_8);
            return objectMapper.readValue(json, FitnessProfile.class);
        } catch (Exception e) {
            log.warn("Health data decryption integrity check failed: {}", e.getClass().getSimpleName());
            throw ApiException.badRequest("Health data integrity check failed or payload corrupted");
        }
    }
}
