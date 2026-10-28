package com.fitflow.auth.event;

public record UserRegisteredEvent(
        Long userId,
        String email,
        String role
) {
}
