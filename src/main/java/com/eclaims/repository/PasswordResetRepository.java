package com.eclaims.repository;

import com.eclaims.entity.PasswordReset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PasswordResetRepository
        extends JpaRepository<PasswordReset, Long> {

    Optional<PasswordReset> findByToken(String token);

    void deleteByStaffId(String staffId);
}