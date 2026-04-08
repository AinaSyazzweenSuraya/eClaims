package com.eclaims.controller;

import com.eclaims.dto.*;
import com.eclaims.entity.*;
import com.eclaims.service.*;
import com.eclaims.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.math.BigDecimal;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final ClaimService   claimService;
    private final UserService    userService;

    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal UserDetails user, Model model) {
        StaffInfo staff = userService.getStaffByUsername(user.getUsername());
        UserAccount ua  = userService.getByUsername(user.getUsername());
        List<ClaimFormDto> claims = claimService.getClaimsForStaff(staff.getStaffId());

        long draft    = claims.stream().filter(c -> "DRAFT".equals(c.getWfStatus())).count();
        long pending  = claims.stream().filter(c ->
            "SUBMITTED".equals(c.getWfStatus()) || "PM_APPROVED".equals(c.getWfStatus())).count();
        long approved = claims.stream().filter(c ->
            "SUPERIOR_APPROVED".equals(c.getWfStatus()) ||
            "FINANCE_PROCESSED".equals(c.getWfStatus())).count();
        BigDecimal totalAmt = claims.stream()
            .map(c -> c.getTotalClaim() != null ? c.getTotalClaim() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<StaffClaimBalanceDto> balances = claimService.getBalances(staff.getStaffId());

        model.addAttribute("staff",       staff);
        model.addAttribute("role",        ua != null ? ua.getRole().name() : "STAFF");
        model.addAttribute("claims",      claims);
        model.addAttribute("draftCount",  draft);
        model.addAttribute("pendingCount",pending);
        model.addAttribute("approvedCount",approved);
        model.addAttribute("totalAmount", totalAmt);
        model.addAttribute("balances",    balances);
        model.addAttribute("activePage",  "dashboard");
        return "dashboard/index";
    }
}
