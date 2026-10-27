package com.fitflow.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "user_roles")
public class UserRole {

    public enum Role {
        MEMBER, TRAINER, ADMIN
    }

    @EmbeddedId
    private UserRoleId id;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @Embeddable
    public static class UserRoleId implements Serializable {
        @Column(name = "user_id", nullable = false)
        private Long userId;

        @Enumerated(EnumType.STRING)
        @Column(name = "role", nullable = false)
        private Role role;
    }
}
