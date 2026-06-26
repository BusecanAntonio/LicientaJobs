package org.example.licientajobs;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/password-reset")
public class PasswordResetController {

    @Autowired
    private StudentService studentService;

    @GetMapping
    public String showForgotPasswordForm() {
        return "forgot-password";
    }

    @PostMapping("/request")
    public String requestPasswordReset(@RequestParam String username, @RequestParam String email, RedirectAttributes redirectAttributes) {
        boolean sent = studentService.generateAndSendResetCode(username, email);
        if (sent) {
            redirectAttributes.addFlashAttribute("message", "A reset code has been sent to your email. Please check your inbox.");
            return "redirect:/password-reset/reset";
        } else {
            redirectAttributes.addFlashAttribute("error", "User not found with the provided username and email, or email sending failed.");
            return "redirect:/password-reset";
        }
    }

    @GetMapping("/reset")
    public String showResetPasswordForm() {
        return "reset-password";
    }

    @PostMapping("/reset")
    public String resetPassword(@RequestParam String username, @RequestParam String code, @RequestParam String password, RedirectAttributes redirectAttributes) {
        boolean reset = studentService.resetPassword(username, code, password);
        if (reset) {
            redirectAttributes.addFlashAttribute("success", "Your password has been reset successfully. You can now log in.");
            return "redirect:/login";
        } else {
            redirectAttributes.addFlashAttribute("error", "Invalid reset code or the code has expired. Please try again.");
            return "redirect:/password-reset/reset";
        }
    }
}