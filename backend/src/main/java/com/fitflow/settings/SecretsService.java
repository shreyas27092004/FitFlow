package com.fitflow.settings;

import com.fitflow.security.EncryptionService;
import com.fitflow.settings.entity.AppSecret;
import com.fitflow.settings.repository.AppSecretRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SecretsService {

    private final AppSecretRepository secretRepository;
    private final EncryptionService encryptionService;

    @Transactional(readOnly = true)
    public Optional<String> get(String name) {
        return secretRepository.findById(name)
                .map(secret -> encryptionService.decryptString(secret.getEncryptedValue()));
    }

    @Transactional
    public void set(String name, String value, Long actorUserId) {
        String encrypted = encryptionService.encryptString(value);
        AppSecret secret = secretRepository.findById(name)
                .orElseGet(() -> AppSecret.builder()
                        .name(name)
                        .keyVersion(1)
                        .build());

        secret.setEncryptedValue(encrypted);
        secret.setUpdatedBy(actorUserId);
        secretRepository.save(secret);
        log.info("App secret '{}' updated by actorUserId={}", name, actorUserId);
    }

    @Transactional
    public void delete(String name) {
        if (secretRepository.existsById(name)) {
            secretRepository.deleteById(name);
            log.info("App secret '{}' deleted", name);
        }
    }

    @Transactional(readOnly = true)
    public boolean exists(String name) {
        return secretRepository.existsById(name);
    }

    @Transactional(readOnly = true)
    public Optional<String> masked(String name) {
        return get(name).map(val -> {
            if (val == null || val.isEmpty()) {
                return "";
            }
            if (val.length() <= 4) {
                return val;
            }
            return val.substring(val.length() - 4);
        });
    }
}
