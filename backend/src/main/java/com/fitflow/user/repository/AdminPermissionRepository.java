package com.fitflow.user.repository;

import com.fitflow.user.entity.AdminPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AdminPermissionRepository extends JpaRepository<AdminPermission, AdminPermission.AdminPermissionId> {
    List<AdminPermission> findByIdUserId(Long userId);

    @Modifying
    @Query("DELETE FROM AdminPermission ap WHERE ap.id.userId = :userId")
    void deleteByIdUserId(@Param("userId") Long userId);

    @Query("SELECT COUNT(DISTINCT ap.id.userId) FROM AdminPermission ap, User u WHERE ap.id.userId = u.id AND ap.id.permission = :permission AND u.status = com.fitflow.user.entity.User.Status.ACTIVE")
    long countActiveUsersWithPermission(@Param("permission") String permission);
}
