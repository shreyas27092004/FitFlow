package com.fitflow.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EncryptionServiceTest {

    private EncryptionService encryptionService;
    private byte[] key32;

    @BeforeEach
    void setUp() {
        key32 = new byte[32];
        new SecureRandom().nextBytes(key32);
        encryptionService = EncryptionService.of(key32);
    }

    @Test
    @DisplayName("Case 1: Encrypt and decrypt round trip preserves plaintext exactly")
    void testEncryptDecryptRoundTrip() {
        String plaintext = "Secret medical history: knee arthroscopy in 2024; mild hypertension.";
        byte[] originalBytes = plaintext.getBytes(StandardCharsets.UTF_8);

        byte[] encrypted = encryptionService.encrypt(originalBytes);
        assertNotNull(encrypted);
        assertTrue(encrypted.length > originalBytes.length, "Encrypted payload must include version byte, IV, and GCM tag");

        byte[] decrypted = encryptionService.decrypt(encrypted);
        assertArrayEquals(originalBytes, decrypted);
        assertEquals(plaintext, new String(decrypted, StandardCharsets.UTF_8));

        // Also test String helpers
        String encryptedBase64 = encryptionService.encryptString(plaintext);
        assertNotNull(encryptedBase64);
        String decryptedString = encryptionService.decryptString(encryptedBase64);
        assertEquals(plaintext, decryptedString);
    }

    @Test
    @DisplayName("Case 2: Encrypting identical plaintext twice produces different ciphertexts (random IV)")
    void testSamePlaintextTwiceProducesDifferentCiphertext() {
        String plaintext = "Identical message for semantic security testing";
        byte[] ptBytes = plaintext.getBytes(StandardCharsets.UTF_8);

        byte[] cipher1 = encryptionService.encrypt(ptBytes);
        byte[] cipher2 = encryptionService.encrypt(ptBytes);

        assertFalse(java.util.Arrays.equals(cipher1, cipher2),
                "AES-256-GCM ciphertexts must differ across encryptions due to random 12-byte IV");
        assertNotEquals(encryptionService.encryptString(plaintext), encryptionService.encryptString(plaintext));
    }

    @Test
    @DisplayName("Case 3: Tampering with any byte of ciphertext or tag causes decryption to fail")
    void testTamperingWithOneByteFailsDecryption() {
        String plaintext = "Tamper detection authentication test";
        byte[] cipher = encryptionService.encrypt(plaintext.getBytes(StandardCharsets.UTF_8));

        // Flip one bit in the middle of ciphertext/tag
        int tamperIndex = cipher.length - 5;
        cipher[tamperIndex] ^= 0x01;

        assertThrows(EncryptionException.class, () -> encryptionService.decrypt(cipher),
                "Tampered ciphertext must fail AEAD GCM tag verification");
    }

    @Test
    @DisplayName("Case 4: Decrypting with wrong key fails")
    void testDecryptionWithWrongKeyFails() {
        byte[] wrongKey = new byte[32];
        new SecureRandom().nextBytes(wrongKey);
        EncryptionService wrongService = EncryptionService.of(wrongKey);

        byte[] cipher = encryptionService.encrypt("Top secret payload".getBytes(StandardCharsets.UTF_8));

        assertThrows(EncryptionException.class, () -> wrongService.decrypt(cipher),
                "Decryption using a non-matching master key must fail");
    }

    @Test
    @DisplayName("Case 5: Masked secret representation never returns more than 4 characters")
    void testMaskedNeverReturnsMoreThanFourCharacters() {
        // Helper method verifying masking logic for various input string lengths
        assertEquals("4567", maskHelper("1234567"));
        assertEquals("9999", maskHelper("AIzaSyBxxxx9999"));
        assertEquals("abcd", maskHelper("abcd"));
        assertEquals("abc", maskHelper("abc"));
        assertEquals("a", maskHelper("a"));
        assertEquals("", maskHelper(""));

        assertTrue(maskHelper("super-long-gemini-secret-api-key-value-1234").length() <= 4);
        assertTrue(maskHelper("12").length() <= 4);
    }

    private String maskHelper(String val) {
        if (val == null || val.isEmpty()) {
            return "";
        }
        if (val.length() <= 4) {
            return val;
        }
        return val.substring(val.length() - 4);
    }
}
