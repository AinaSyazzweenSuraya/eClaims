package com.eclaims.repository;

import com.eclaims.entity.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserAccountRepository extends JpaRepository<UserAccount, Integer> {

    Optional<UserAccount> findByUsername(String username);

    Optional<UserAccount> findByStaffId(String staffId);

    boolean existsByUsername(String username);

    // Fixed: use enum references instead of string literals
    List<UserAccount> findByRoleInAndIsActiveTrue(List<UserAccount.Role> roles);

    // Find all active SUPERIOR users for ApproverID dropdown
    List<UserAccount> findByRoleAndIsActiveTrue(UserAccount.Role role);
}
