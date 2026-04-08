package com.eclaims.service;

import com.eclaims.entity.*;
import com.eclaims.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserAccountRepository  userAccountRepository;
    private final StaffInfoRepository    staffInfoRepository;
    private final DepartmentRepository   departmentRepository;
    private final CompanyRepository      companyRepository;
    private final PasswordEncoder        passwordEncoder;

    public UserAccount getByUsername(String username) {
        return userAccountRepository.findByUsername(username).orElse(null);
    }

    public StaffInfo getStaffByUsername(String username) {
        UserAccount ua = getByUsername(username);
        if (ua == null) return null;
        StaffInfo staff = staffInfoRepository.findById(ua.getStaffId()).orElse(null);
        if (staff != null) {
            // Safely look up department name
            if (staff.getDepartmentId() != null && !staff.getDepartmentId().isBlank()) {
                departmentRepository.findById(staff.getDepartmentId())
                    .ifPresent(d -> staff.setDepartmentName(d.getDepartmentName()));
            }
            // Safely look up company name
            if (staff.getCompanyId() != null && !staff.getCompanyId().isBlank()) {
                companyRepository.findById(staff.getCompanyId())
                    .ifPresent(c -> staff.setCompanyName(c.getCompanyName()));
            }
        }
        return staff;
    }

    public List<StaffInfo> getAllStaff() {
        return staffInfoRepository.findAllByOrderByName();
    }

    public List<UserAccount> getAllAccounts() {
        return userAccountRepository.findAll();
    }

    @Transactional
    public UserAccount createAccount(String staffId, String password, UserAccount.Role role) {
        if (userAccountRepository.existsByUsername(staffId))
            throw new IllegalArgumentException("Account already exists for staff: " + staffId);
        staffInfoRepository.findById(staffId)
            .orElseThrow(() -> new IllegalArgumentException("Staff not found: " + staffId));

        UserAccount ua = UserAccount.builder()
            .staffId(staffId)
            .username(staffId)
            .password(passwordEncoder.encode(password))
            .role(role)
            .isActive(true)
            .build();
        return userAccountRepository.save(ua);
    }

    @Transactional
    public void changePassword(String username, String oldPassword, String newPassword) {
        UserAccount ua = userAccountRepository.findByUsername(username)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (!passwordEncoder.matches(oldPassword, ua.getPassword()))
            throw new IllegalArgumentException("Current password is incorrect");
        ua.setPassword(passwordEncoder.encode(newPassword));
        userAccountRepository.save(ua);
    }

    @Transactional
    public void recordLogin(String username) {
        userAccountRepository.findByUsername(username).ifPresent(ua -> {
            ua.setLastLogin(LocalDateTime.now());
            userAccountRepository.save(ua);
        });
    }

    @Transactional
    public void toggleActive(Integer userId) {
        userAccountRepository.findById(userId).ifPresent(ua -> {
            ua.setIsActive(!ua.getIsActive());
            userAccountRepository.save(ua);
        });
    }

    /**
     * Resets a user's password to the default value "password".
     * Called by Admin from the User Accounts page.
     * Staff must change their password after logging in via Profile → Change Password.
     */
    @Transactional
    public void resetPassword(Integer userId) {
        UserAccount ua = userAccountRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("Account not found: " + userId));
        // Reset to default password — staff must change on next login
        ua.setPassword(passwordEncoder.encode("password"));
        userAccountRepository.save(ua);
    }

    /**
     * Saves a new staff member to EN_TBL_MAST_STAFF_INFO.
     * Called from Admin → Staff Registration form.
     */
    @Transactional
    public StaffInfo saveStaff(StaffInfo staff) {
        if (staffInfoRepository.existsById(staff.getStaffId()))
            throw new IllegalArgumentException("Staff ID already exists: " + staff.getStaffId());
        staff.setDateCreated(java.time.LocalDateTime.now());
        staff.setDateModified(java.time.LocalDateTime.now());
        return staffInfoRepository.save(staff);
    }

    /**
     * Returns all active SUPERIOR users — used for ApproverID dropdown.
     */
    public List<StaffInfo> getSuperiorStaff() {
        return userAccountRepository
            .findByRoleAndIsActiveTrue(UserAccount.Role.SUPERIOR)
            .stream()
            .map(ua -> staffInfoRepository.findById(ua.getStaffId()).orElse(null))
            .filter(java.util.Objects::nonNull)
            .sorted(java.util.Comparator.comparing(StaffInfo::getName))
            .collect(java.util.stream.Collectors.toList());
    }
}
