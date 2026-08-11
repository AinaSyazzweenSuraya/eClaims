package com.eclaims.controller;

import com.eclaims.entity.PasswordReset;
import com.eclaims.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
@Slf4j
public class ForgotPasswordController {

    private final UserService userService;

    // Show Forget Password Page
    @GetMapping("/forgot-password")
    public String showForgotPasswordPage() {
        return "auth/forgot-password";
    }

    // Process Forgot Password Request
    @PostMapping("/forgot-password")
    public String processForgotPassword(
            @RequestParam String staffId,
            RedirectAttributes ra) {

        System.out.println(
                "========== FORGOT PASSWORD CONTROLLER HIT =========="
        );

        System.out.println(
                "Staff ID received: " + staffId
        );

        try {

            log.info(
                    "Forgot password request received for Staff ID: {}",
                    staffId
            );

            userService.processForgotPassword(staffId);

            log.info(
                    "Forgot password process completed successfully for Staff ID: {}",
                    staffId
            );

            ra.addFlashAttribute(
                    "successMessage",
                    "A password reset link has been sent to the registered email address."
            );

        } catch (Exception e) {

            e.printStackTrace();

            log.error(
                    "Forgot password process failed for Staff ID: {}",
                    staffId,
                    e
            );

            ra.addFlashAttribute(
                    "errorMessage",
                    "ERROR: " + e.getClass().getSimpleName()
                            + " - " + e.getMessage()
            );
        }

        return "redirect:/forgot-password";
    }


    // Show Reset Password
    @GetMapping("/reset-password")
    public String showResetPasswordPage(
            @RequestParam("token") String token,
            Model model) {

        log.info("Reset password page requested with token: {}", token);

        try {

            // Validate token
            PasswordReset resetToken =
                    userService.validateResetToken(token);

            // Pass token to the reset password form
            model.addAttribute("token", token);

            return "auth/reset-password";

        } catch (IllegalArgumentException e) {

            log.error(
                    "Invalid password reset token: {}",
                    e.getMessage()
            );

            model.addAttribute(
                    "errorMessage",
                    e.getMessage()
            );

            return "auth/reset-password";
        }
    }

    // Process Reset Password
    @PostMapping("/reset-password")
    public String processResetPassword(
            @RequestParam("token") String token,
            @RequestParam("password") String password,
            @RequestParam("confirmPassword") String confirmPassword,
            RedirectAttributes ra) {

        log.info(
                "Password reset request received"
        );

        try {

            // Check whether passwords match
            if (!password.equals(confirmPassword)) {

                ra.addFlashAttribute(
                        "errorMessage",
                        "Passwords do not match."
                );

                return "redirect:/reset-password?token=" + token;
            }


            // Reset password using token
            userService.resetPassword(
                    token,
                    password
            );


            log.info(
                    "Password reset completed successfully"
            );


            ra.addFlashAttribute(
                    "successMessage",
                    "Your password has been reset successfully. You can now log in."
            );

            return "redirect:/login";


        } catch (Exception e) {

            log.error(
                    "Password reset failed",
                    e
            );

            ra.addFlashAttribute(
                    "errorMessage",
                    "Unable to reset your password. Please try again later."
            );

            return "redirect:/reset-password?token=" + token;
        }
    }

}
