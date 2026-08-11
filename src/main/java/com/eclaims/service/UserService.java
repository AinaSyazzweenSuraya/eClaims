package com.eclaims.service;

import com.eclaims.entity.*;
import com.eclaims.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserAccountRepository  userAccountRepository;
    private final StaffInfoRepository    staffInfoRepository;
    private final DepartmentRepository   departmentRepository;
    private final CompanyRepository      companyRepository;
    private final PasswordEncoder        passwordEncoder;
    private final PasswordResetRepository passwordResetRepository;
    private final EmailService emailService;

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

    /**
     * Process Forgot Password request.
     * 1. Find UserAccount using Staff ID
     * 2. Get registered email
     * 3. Generate secure reset token
     * 4. Save token
     * 5. Send reset email
     */
    @Transactional
    public void processForgotPassword(String staffId) {

        // Find staff using Staff ID
        StaffInfo staff = staffInfoRepository
                .findById(staffId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Staff ID not found: " + staffId
                        )
                );

        String email = staff.getEmail();

        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException(
                    "No email address registered for Staff ID: " + staffId
            );
        }

        // Check that UserAccount exists using Staff ID
        UserAccount userAccount = userAccountRepository
                .findByStaffId(staffId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "User account not found for Staff ID: " + staffId
                        )
                );

        // Delete old reset token
        passwordResetRepository.deleteByStaffId(staffId);

        // Generate token
        String token = UUID.randomUUID().toString();

        LocalDateTime expiryDate =
                LocalDateTime.now().plusMinutes(30);

        PasswordReset resetToken =
                PasswordReset.builder()
                        .staffId(staffId)
                        .token(token)
                        .expiryDate(expiryDate)
                        .used(false)
                        .dateCreated(LocalDateTime.now())
                        .build();

        passwordResetRepository.save(resetToken);

        String resetLink =
                "http://localhost:8081/reset-password?token="
                        + token;

        emailService.sendPasswordResetEmail(
                email,
                resetLink
        );
    }

    /**
     * Validates a password reset token.
     */
    public PasswordReset validateResetToken(String token) {

        PasswordReset resetToken =
                passwordResetRepository
                        .findByToken(token)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Invalid password reset token"
                                )
                        );

        // Check if token has already been used
        if (resetToken.isUsed()) {
            throw new IllegalArgumentException(
                    "Password reset token has already been used"
            );
        }

        // Check expiry
        if (resetToken.getExpiryDate()
                .isBefore(LocalDateTime.now())) {

            throw new IllegalArgumentException(
                    "Password reset token has expired"
            );
        }

        return resetToken;
    }


    /**
     * Reset password using a valid reset token.
     */
    @Transactional
    public void resetPassword(
            String token,
            String newPassword) {

        // Validate token
        PasswordReset resetToken =
                validateResetToken(token);

        // Find user account using Staff ID
        UserAccount userAccount =
                userAccountRepository
                        .findByStaffId(
                                resetToken.getStaffId()
                        )
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "User account not found for Staff ID: "
                                                + resetToken.getStaffId()
                                )
                        );

        log.info(
                "User account found for password reset. Staff ID: {}",
                resetToken.getStaffId()
        );

        // Encode new password
        userAccount.setPassword(
                passwordEncoder.encode(newPassword)
        );

        // Save updated password
        userAccountRepository.save(userAccount);

        log.info(
                "Password updated successfully for Staff ID: {}",
                resetToken.getStaffId()
        );

        // Mark token as used
        resetToken.setUsed(true);

        passwordResetRepository.save(resetToken);

        log.info(
                "Password reset token marked as used for Staff ID: {}",
                resetToken.getStaffId()
        );
    }
}
