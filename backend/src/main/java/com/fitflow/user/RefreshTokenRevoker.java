package com.fitflow.user;

public interface RefreshTokenRevoker {
    void revokeAllForUser(Long userId);
}
