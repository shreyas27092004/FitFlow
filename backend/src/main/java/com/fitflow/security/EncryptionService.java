package com.fitflow.security;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Slf4j
@Service
public class EncryptionService {

    public static final byte CURRENT_KEY_VERSION = 0x01;
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    private final String configuredMasterKey;
    private final Environment environment;
    private final SecureRandom secureRandom = new SecureRandom();

    private byte[] masterKeyBytes;

    public EncryptionService(
            @Value("${fitflow.security.master-key:}") String configuredMasterKey,
            Environment environment) {
        this.configuredMasterKey = configuredMasterKey;
        this.environment = environment;
    }

    // Factory method for testing with an explicit key byte array
    public static EncryptionService of(byte[] keyBytes) {
        if (keyBytes == null || keyBytes.length != 32) {
            throw new IllegalArgumentException("Master key must be exactly 32 bytes (256 bits)");
        }
        EncryptionService service = new EncryptionService(null, null);
        service.masterKeyBytes = Arrays.copyOf(keyBytes, keyBytes.length);
        return service;
    }

    @PostConstruct
    public void init() {
        if (this.masterKeyBytes != null) {
            return;
        }

        boolean isProd = environment != null && Arrays.asList(environment.getActiveProfiles()).contains("prod");

        if (configuredMasterKey != null && !configuredMasterKey.isBlank()) {
            try {
                byte[] decoded = Base64.getDecoder().decode(configuredMasterKey.trim());
                if (decoded.length != 32) {
                    throw new IllegalArgumentException("APP_MASTER_KEY must be a Base64-encoded 32-byte key (256-bit)");
                }
                this.masterKeyBytes = decoded;
                return;
            } catch (IllegalArgumentException e) {
                if (isProd) {
                    throw new IllegalStateException("Invalid APP_MASTER_KEY configured in production", e);
                }
                log.warn("Invalid APP_MASTER_KEY configured; falling back to local master key");
            }
        }

        if (isProd) {
            throw new IllegalStateException("Production startup failed: APP_MASTER_KEY environment variable is required and must not be blank.");
        }

        // In dev / test: check or generate .local/master.key
        this.masterKeyBytes = loadOrGenerateLocalDevKey();
    }

    private byte[] loadOrGenerateLocalDevKey() {
        Path localDir = Path.of(".local");
        Path keyFile = localDir.resolve("master.key");

        try {
            if (Files.exists(keyFile)) {
                String encoded = Files.readString(keyFile).trim();
                byte[] decoded = Base64.getDecoder().decode(encoded);
                if (decoded.length == 32) {
                    return decoded;
                }
            }

            Files.createDirectories(localDir);
            byte[] generated = new byte[32];
            secureRandom.nextBytes(generated);
            String encoded = Base64.getEncoder().encodeToString(generated);
            Files.writeString(keyFile, encoded);

            File f = keyFile.toFile();
            f.setReadable(false, false);
            f.setReadable(true, true);
            f.setWritable(false, false);
            f.setWritable(true, true);

            log.warn("No APP_MASTER_KEY found in dev mode. Generated local master key at .local/master.key");
            return generated;
        } catch (IOException e) {
            log.warn("Could not persist .local/master.key, using in-memory ephemeral key for this session: {}", e.getMessage());
            byte[] generated = new byte[32];
            secureRandom.nextBytes(generated);
            return generated;
        }
    }

    public byte[] encrypt(byte[] plaintext) {
        return encrypt(plaintext, this.masterKeyBytes, CURRENT_KEY_VERSION);
    }

    public byte[] encrypt(byte[] plaintext, byte[] keyBytes, byte version) {
        if (plaintext == null) {
            return null;
        }
        if (keyBytes == null || keyBytes.length != 32) {
            throw new EncryptionException("Encryption key must be exactly 32 bytes");
        }

        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            SecretKey secretKey = new SecretKeySpec(keyBytes, ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);

            byte[] cipherText = cipher.doFinal(plaintext);

            // Output format = version byte (1 byte) + IV (12 bytes) + ciphertext+tag
            ByteBuffer buffer = ByteBuffer.allocate(1 + iv.length + cipherText.length);
            buffer.put(version);
            buffer.put(iv);
            buffer.put(cipherText);
            return buffer.array();
        } catch (Exception e) {
            throw new EncryptionException("Failed to encrypt data", e);
        }
    }

    public byte[] decrypt(byte[] payload) {
        return decrypt(payload, this.masterKeyBytes);
    }

    public byte[] decrypt(byte[] payload, byte[] keyBytes) {
        if (payload == null) {
            return null;
        }
        if (payload.length < 1 + IV_LENGTH_BYTES + (TAG_LENGTH_BITS / 8)) {
            throw new EncryptionException("Ciphertext payload too short to be valid AES-GCM data");
        }
        if (keyBytes == null || keyBytes.length != 32) {
            throw new EncryptionException("Decryption key must be exactly 32 bytes");
        }

        try {
            ByteBuffer buffer = ByteBuffer.wrap(payload);
            byte version = buffer.get();
            if (version != CURRENT_KEY_VERSION) {
                throw new EncryptionException("Unsupported key version: " + version);
            }

            byte[] iv = new byte[IV_LENGTH_BYTES];
            buffer.get(iv);

            byte[] cipherTextWithTag = new byte[buffer.remaining()];
            buffer.get(cipherTextWithTag);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            SecretKey secretKey = new SecretKeySpec(keyBytes, ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);

            return cipher.doFinal(cipherTextWithTag);
        } catch (EncryptionException ee) {
            throw ee;
        } catch (Exception e) {
            throw new EncryptionException("Decryption failed: integrity check failed or invalid key", e);
        }
    }

    public String encryptString(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        byte[] encrypted = encrypt(plaintext.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encrypted);
    }

    public String decryptString(String ciphertextBase64) {
        if (ciphertextBase64 == null) {
            return null;
        }
        byte[] payload = Base64.getDecoder().decode(ciphertextBase64.trim());
        byte[] decrypted = decrypt(payload);
        return new String(decrypted, StandardCharsets.UTF_8);
    }
}
