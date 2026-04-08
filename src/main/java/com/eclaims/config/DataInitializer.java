package com.eclaims.config;

import com.eclaims.entity.*;
import com.eclaims.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class DataInitializer implements CommandLineRunner {

    @Autowired private UserAccountRepository userAccountRepository;
    @Autowired private StaffInfoRepository   staffInfoRepository;
    @Autowired private PasswordEncoder       passwordEncoder;

    @Override
    public void run(String... args) {
        if (userAccountRepository.count() > 0) {
            log.info("User accounts exist ({}) — skipping seed.", userAccountRepository.count());
            return;
        }
        // No accounts yet — create one from the first staff record
        staffInfoRepository.findAll().stream().findFirst().ifPresentOrElse(staff -> {
            UserAccount admin = new UserAccount();
            admin.setStaffId(staff.getStaffId());
            admin.setUsername(staff.getStaffId());
            admin.setPassword(passwordEncoder.encode("Admin@1234"));
            admin.setRole(UserAccount.Role.ADMIN);
            admin.setIsActive(true);
            userAccountRepository.save(admin);
            log.info("=== DEFAULT ADMIN CREATED ===");
            log.info("  Username : {}", staff.getStaffId());
            log.info("  Password : Admin@1234");
            log.info("  CHANGE THIS PASSWORD AFTER FIRST LOGIN");
        }, () -> log.warn("No staff in EN_TBL_MAST_STAFF_INFO — add staff first, then restart."));
    }
}
