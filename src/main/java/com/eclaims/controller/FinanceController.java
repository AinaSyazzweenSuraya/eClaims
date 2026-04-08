package com.eclaims.controller;

import com.eclaims.dto.ClaimFormDto;
import com.eclaims.entity.*;
import com.eclaims.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/finance")
@RequiredArgsConstructor
public class FinanceController {

    private final ClaimService claimService;
    private final UserService  userService;

    @GetMapping("/claims")
    public String financeClaims(@AuthenticationPrincipal UserDetails user, Model model) {
        StaffInfo staff    = userService.getStaffByUsername(user.getUsername());
        UserAccount ua     = userService.getByUsername(user.getUsername());
        List<ClaimFormDto> pending = claimService.getPendingForFinance();

        model.addAttribute("staff",      staff);
        model.addAttribute("role",       ua != null ? ua.getRole().name() : "FINANCE");
        model.addAttribute("claims",     pending);
        model.addAttribute("activePage", "finance");
        return "finance/claims";
    }

    @GetMapping("/claims/{workflowId}")
    public String viewClaim(@PathVariable String workflowId,
                             @AuthenticationPrincipal UserDetails user, Model model) {
        StaffInfo staff = userService.getStaffByUsername(user.getUsername());
        ClaimFormDto form = claimService.loadForm(workflowId);
        List balances = claimService.getBalances(form.getStaffId());
        model.addAttribute("form",     form);
        model.addAttribute("staff",    staff);
        model.addAttribute("balances", balances);
        model.addAttribute("activePage", "finance");
        return "finance/review";
    }

    @PostMapping("/claims/{workflowId}/process")
    public String processClaim(@PathVariable String workflowId,
                                @RequestParam(required = false) String remarks,
                                @AuthenticationPrincipal UserDetails user,
                                RedirectAttributes ra) {
        StaffInfo staff = userService.getStaffByUsername(user.getUsername());
        claimService.financeProcess(workflowId, staff.getStaffId(), remarks);
        ra.addFlashAttribute("successMsg", "Claim processed successfully.");
        return "redirect:/finance/claims";
    }
}
