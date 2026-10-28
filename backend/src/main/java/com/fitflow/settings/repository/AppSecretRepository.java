package com.fitflow.settings.repository;

import com.fitflow.settings.entity.AppSecret;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AppSecretRepository extends JpaRepository<AppSecret, String> {
}
