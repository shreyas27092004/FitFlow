package com.fitflow.user.dto;

public record NotificationPrefsDto(
        Boolean emailBookings,
        Boolean emailReminders,
        Boolean emailMarketing
) {
    public static NotificationPrefsDto defaultPrefs() {
        return new NotificationPrefsDto(true, true, false);
    }
}
