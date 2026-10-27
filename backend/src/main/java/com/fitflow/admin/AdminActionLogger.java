package com.fitflow.admin;

import com.fitflow.admin.entity.AdminActionLog;
import com.fitflow.admin.repository.AdminActionLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminActionLogger {

    private static final Pattern SENSITIVE_KEY_PATTERN = Pattern.compile(
            "\"(password|apiKey|token|secret)\"\\s*:",
            Pattern.CASE_INSENSITIVE
    );

    private final AdminActionLogRepository adminActionLogRepository;
    private final Clock clock;

    public AdminActionLog logAction(
            Long adminId,
            String action,
            String targetType,
            Long targetId,
            String beforeJson,
            String afterJson,
            String ipHash) {

        validateJsonPayload(beforeJson, "beforeJson");
        validateJsonPayload(afterJson, "afterJson");

        AdminActionLog entry = AdminActionLog.builder()
                .adminId(adminId)
                .action(action)
                .targetType(targetType)
                .targetId(targetId)
                .beforeJson(beforeJson)
                .afterJson(afterJson)
                .ipHash(ipHash)
                .createdAt(clock.instant())
                .build();

        return adminActionLogRepository.save(entry);
    }

    private void validateJsonPayload(String json, String fieldName) {
        if (json == null || json.isBlank()) {
            return;
        }
        if (SENSITIVE_KEY_PATTERN.matcher(json).find()) {
            throw new IllegalArgumentException(
                    "AdminActionLogger refuses to store payloads containing sensitive keys (password, token, apiKey, secret) in " + fieldName
            );
        }
    }
}
