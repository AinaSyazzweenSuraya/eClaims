package com.eclaims.controller;

import com.eclaims.entity.StaffInfo;
import com.eclaims.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final UserService userService;

    @GetMapping
    public String profile(@AuthenticationPrincipal UserDetails user, Model model) {
        StaffInfo staff = userService.getStaffByUsername(user.getUsername());
        model.addAttribute("staff",      staff);
        model.addAttribute("account",    userService.getByUsername(user.getUsername()));
        model.addAttribute("activePage", "profile");
        return "profile";
    }

    @PostMapping("/change-password")
    public String changePassword(@AuthenticationPrincipal UserDetails user,
                                  @RequestParam String oldPassword,
                                  @RequestParam String newPassword,
                                  @RequestParam String confirmPassword,
                                  RedirectAttributes ra) {
        if (!newPassword.equals(confirmPassword)) {
            ra.addFlashAttribute("pwdError", "Passwords do not match.");
            return "redirect:/profile";
        }
        if (newPassword.length() < 8) {
            ra.addFlashAttribute("pwdError", "Password must be at least 8 characters.");
            return "redirect:/profile";
        }
        try {
            userService.changePassword(user.getUsername(), oldPassword, newPassword);
            ra.addFlashAttribute("pwdSuccess", "Password changed successfully.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("pwdError", e.getMessage());
        }
        return "redirect:/profile";
    }
}
