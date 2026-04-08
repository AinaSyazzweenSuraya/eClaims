package com.eclaims.controller;

import com.eclaims.dto.ClaimFormDto;
import com.eclaims.dto.ClaimFormDto;
import com.eclaims.entity.*;
import com.eclaims.repository.ClaimFormRowRepository;
import com.eclaims.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/approval")
@RequiredArgsConstructor
public class ApprovalController {

    private final ClaimService           claimService;
    private final UserService            userService;
    private final ClaimFormRowRepository rowRepository;

    // ── PM Dashboard ─────────────────────────────────────────
    @GetMapping("/pm")
    public String pmDashboard(@AuthenticationPrincipal UserDetails user, Model model) {
        StaffInfo staff = userService.getStaffByUsername(user.getUsername());
        UserAccount ua  = userService.getByUsername(user.getUsername());

        // Find claims with rows pending for this PM
        List<String> wfIds = rowRepository.findWorkflowIdsPendingForPm(staff.getStaffId());
        List<ClaimFormDto> pending = wfIds.stream()
            .map(id -> { try { return claimService.loadForm(id); } catch (Exception e) { return null; } })
            .filter(f -> f != null).collect(Collectors.toList());

        model.addAttribute("staff",      staff);
        model.addAttribute("role",       ua != null ? ua.getRole().name() : "MANAGER");
        model.addAttribute("claims",     pending);
        model.addAttribute("activePage", "approvalPm");
        return "approval/pm-dashboard";
    }

    // ── PM view single claim ──────────────────────────────────
    @GetMapping("/pm/{workflowId}")
    public String pmViewClaim(@PathVariable String workflowId,
                               @AuthenticationPrincipal UserDetails user, Model model) {
        StaffInfo staff = userService.getStaffByUsername(user.getUsername());
        ClaimFormDto form = claimService.loadForm(workflowId);
        model.addAttribute("form",    form);
        model.addAttribute("staff",   staff);
        model.addAttribute("pmId",    staff.getStaffId());
        model.addAttribute("activePage", "approvalPm");
        return "approval/pm-review";
    }

    // ── PM approve/reject ─────────────────────────────────────
    @PostMapping("/pm/{workflowId}")
    public String pmAction(@PathVariable String workflowId,
                            @RequestParam String action,
                            @RequestParam(required = false) String remarks,
                            @AuthenticationPrincipal UserDetails user,
                            RedirectAttributes ra) {
        StaffInfo staff = userService.getStaffByUsername(user.getUsername());
        claimService.pmAction(workflowId, staff.getStaffId(), action, remarks);
        ra.addFlashAttribute("successMsg", "Claim " + action + "d successfully.");
        return "redirect:/approval/pm";
    }

    // ── Superior Dashboard ────────────────────────────────────
    @GetMapping("/superior")
    public String superiorDashboard(@AuthenticationPrincipal UserDetails user, Model model) {
        StaffInfo staff = userService.getStaffByUsername(user.getUsername());
        UserAccount ua  = userService.getByUsername(user.getUsername());

        // Claims where this person is the ApproverID and status is SUBMITTED or PM_APPROVED
        List<ClaimFormDto> pending = claimService.getClaimsForSuperior(staff.getStaffId());

        model.addAttribute("staff",      staff);
        model.addAttribute("role",       ua != null ? ua.getRole().name() : "SUPERIOR");
        model.addAttribute("claims",     pending);
        model.addAttribute("activePage", "approvalSuperior");
        return "approval/superior-dashboard";
    }

    // ── Superior view single claim ────────────────────────────
    @GetMapping("/superior/{workflowId}")
    public String superiorViewClaim(@PathVariable String workflowId,
                                     @AuthenticationPrincipal UserDetails user, Model model) {
        StaffInfo staff = userService.getStaffByUsername(user.getUsername());
        ClaimFormDto form = claimService.loadForm(workflowId);
        model.addAttribute("form",  form);
        model.addAttribute("staff", staff);
        model.addAttribute("activePage", "approvalSuperior");
        return "approval/superior-review";
    }

    // ── Superior approve/reject ───────────────────────────────
    @PostMapping("/superior/{workflowId}")
    public String superiorAction(@PathVariable String workflowId,
                                  @RequestParam String action,
                                  @RequestParam(required = false) String remarks,
                                  @AuthenticationPrincipal UserDetails user,
                                  RedirectAttributes ra) {
        StaffInfo staff = userService.getStaffByUsername(user.getUsername());
        claimService.superiorAction(workflowId, staff.getStaffId(), action, remarks);
        ra.addFlashAttribute("successMsg", "Claim " + action + "d successfully.");
        return "redirect:/approval/superior";
    }
}
