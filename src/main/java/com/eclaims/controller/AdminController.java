package com.eclaims.controller;

import com.eclaims.entity.*;
import com.eclaims.repository.*;
import com.eclaims.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserService          userService;
    private final ClaimService         claimService;
    private final StaffInfoRepository  staffInfoRepository;
    private final DepartmentRepository departmentRepository;
    private final CompanyRepository    companyRepository;

    @GetMapping("/users")
    public String users(@AuthenticationPrincipal UserDetails user, Model model) {
        StaffInfo staff = userService.getStaffByUsername(user.getUsername());
        model.addAttribute("staff",      staff);
        model.addAttribute("accounts",   userService.getAllAccounts());
        model.addAttribute("allStaff",   userService.getAllStaff());
        model.addAttribute("roles",      UserAccount.Role.values());
        model.addAttribute("activePage", "adminUsers");

        // Build staffId -> name lookup map for displaying names in the table
        java.util.Map<String, String> staffNameMap = new java.util.HashMap<>();
        userService.getAllStaff().forEach(s -> staffNameMap.put(s.getStaffId(), s.getName()));
        model.addAttribute("staffNameMap", staffNameMap);
        return "admin/users";
    }

    @PostMapping("/users/create")
    public String createUser(@RequestParam String staffId,
                              @RequestParam String password,
                              @RequestParam UserAccount.Role role,
                              RedirectAttributes ra) {
        try {
            userService.createAccount(staffId, password, role);
            ra.addFlashAttribute("successMsg", "Account created for: " + staffId);
        } catch (Exception e) {
            ra.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}/toggle")
    public String toggleUser(@PathVariable Integer id, RedirectAttributes ra) {
        userService.toggleActive(id);
        ra.addFlashAttribute("successMsg", "Account status updated.");
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}/reset-password")
    public String resetPassword(@PathVariable Integer id, RedirectAttributes ra) {
        try {
            userService.resetPassword(id);
            ra.addFlashAttribute("successMsg",
                "Password reset successfully. Staff can now login with password: password");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    // ── Staff Registration ───────────────────────────────────────
    @GetMapping("/staff/register")
    public String registerStaffForm(@AuthenticationPrincipal UserDetails user, Model model) {
        model.addAttribute("staff",       userService.getStaffByUsername(user.getUsername()));
        model.addAttribute("departments", departmentRepository.findAllByOrderByDepartmentName());
        model.addAttribute("companies",   companyRepository.findAll()
            .stream().sorted(java.util.Comparator.comparing(
                com.eclaims.entity.Company::getCompanyName)).collect(java.util.stream.Collectors.toList()));
        model.addAttribute("superiors",   userService.getSuperiorStaff());
        model.addAttribute("activePage",  "adminStaffRegister");
        return "admin/staff-register";
    }

    @PostMapping("/staff/register")
    public String registerStaff(
            @RequestParam String staffId,
            @RequestParam String name,
            @RequestParam String designation,
            @RequestParam String gender,
            @RequestParam String departmentId,
            @RequestParam String companyId,
            @RequestParam String email,
            @RequestParam(required = false) String categoryId,
            @RequestParam String dateJoined,
            @RequestParam(required = false) String approverId,
            RedirectAttributes ra) {
        try {
            com.eclaims.entity.StaffInfo staff = com.eclaims.entity.StaffInfo.builder()
                .staffId(staffId.trim().toUpperCase())
                .name(name.trim())
                .designation(designation.trim())
                .gender(gender)
                .departmentId(departmentId)
                .companyId(companyId)
                .email(email.trim())
                .categoryId(categoryId != null && !categoryId.isBlank() ? categoryId.trim() : null)
                .dateJoined(dateJoined != null && !dateJoined.isBlank()
                    ? java.time.LocalDate.parse(dateJoined) : null)
                .approverId(approverId != null && !approverId.isBlank() ? approverId : null)
                .build();

            userService.saveStaff(staff);
            ra.addFlashAttribute("successMsg",
                "Staff " + name + " (" + staffId.toUpperCase() + ") registered successfully! You can now create their login account.");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMsg", e.getMessage());
            return "redirect:/admin/staff/register";
        }
        // Redirect to create account page so Admin can immediately create login
        return "redirect:/admin/users";
    }

    @GetMapping("/claims")
    public String allClaims(@AuthenticationPrincipal UserDetails user, Model model) {
        StaffInfo staff = userService.getStaffByUsername(user.getUsername());
        model.addAttribute("staff",      staff);
        model.addAttribute("claims",     claimService.getAllClaims());
        model.addAttribute("activePage", "adminClaims");
        return "admin/claims";
    }
}
