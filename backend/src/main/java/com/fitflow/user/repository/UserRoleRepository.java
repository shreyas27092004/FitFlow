package com.fitflow.user.repository;

import com.fitflow.user.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserRoleRepository extends JpaRepository<UserRole, UserRole.UserRoleId> {
    List<UserRole> findByIdUserId(Long userId);
    boolean existsByIdUserIdAndIdRole(Long userId, UserRole.Role role);
}
