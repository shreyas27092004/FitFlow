package com.fitflow.wellness.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

public record HealthExportResponse(
        @JsonProperty("profile")
        FitnessProfile profile,

        @JsonProperty("metrics")
        List<BodyMetricResponse> metrics,

        @JsonProperty("progress")
        List<ExportProgressItem> progress,

        @JsonProperty("feedback")
        List<ExportFeedbackItem> feedback,

        @JsonProperty("consentHistory")
        List<ExportConsentItem> consentHistory,

        @JsonProperty("accessLogs")
        List<ExportAccessLogItem> accessLogs
) {
    public record ExportProgressItem(
            Long id,
            String logDate,
            String kind,
            String value,
            String note,
            Instant createdAt
    ) {}

    public record ExportFeedbackItem(
            Long id,
            Long bookingId,
            int rating,
            boolean feltPain,
            String painAreaCode,
            String comment,
            Instant createdAt
    ) {}

    public record ExportConsentItem(
            Long id,
            String consentVersion,
            String purpose,
            Instant grantedAt,
            Instant revokedAt,
            Instant createdAt
    ) {}

    public record ExportAccessLogItem(
            Long id,
            Long actorUserId,
            String action,
            String purpose,
            Instant createdAt
    ) {}

    @Override
    public String toString() {
        return "HealthExportResponse[REDACTED]";
    }
}
