package com.fitflow.user.dto;

import java.util.List;
import java.util.Map;

public record UserDataExportResponse(
        UserProfileResponse profile,
        List<Map<String, Object>> bookings,
        List<Map<String, Object>> memberships,
        List<Map<String, Object>> payments
) {}
