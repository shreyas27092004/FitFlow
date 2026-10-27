package com.fitflow.user.repository;

import com.fitflow.user.entity.AdminPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AdminPermissionRepository extends JpaRepository<AdminPermission, AdminPermission.AdminPermissionId> {
    List<AdminPermission> findByIdUserId(Long userId);
}
