package com.eclaims.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.eclaims.entity.ClaimForm;
import com.eclaims.entity.ProjectManager;
import com.eclaims.entity.StaffInfo;
import com.eclaims.entity.UserAccount;
import com.eclaims.repository.ProjectManagerRepository;
import com.eclaims.repository.StaffInfoRepository;
import com.eclaims.repository.UserAccountRepository;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender          mailSender;
    private final ProjectManagerRepository pmRepository;
    private final StaffInfoRepository      staffInfoRepository;
    private final UserAccountRepository    userAccountRepository;

    @Value("${eclaims.mail.enabled:false}") private boolean enabled;
    @Value("${eclaims.mail.from:noreply@eclaims.com}") private String from;
    @Value("${eclaims.app.base-url:http://localhost:8080}") private String baseUrl;

    @Async
    public void sendSubmittedToPm(ClaimForm form, StaffInfo staff, String pmId) {
        if (!enabled) { log.debug("Mail disabled - skipping PM notification"); return; }
        ProjectManager pm = pmRepository.findById(pmId).orElse(null);
        if (pm == null || pm.getEmail() == null) return;

        String subject = "Action Required: Claim Approval - Form No. " + form.getFormId();
        String body = buildHtml(
            "🔔 Claim Pending Your Approval",
            "action",
            "A claim has been submitted and requires your approval.",
            form, staff,
            "Review & Approve", baseUrl + "/approval/pm/" + form.getWorkflowId()
        );
        send(pm.getEmail(), subject, body);
    }

    @Async
    public void sendSubmittedToSuperior(ClaimForm form, StaffInfo staff) {
        if (!enabled) { log.debug("Mail disabled - skipping Superior notification"); return; }
        if (staff == null || staff.getApproverId() == null) return;
        StaffInfo superior = staffInfoRepository.findById(staff.getApproverId()).orElse(null);
        if (superior == null || superior.getEmail() == null) return;

        String subject = "Action Required: Claim Approval - Form No. " + form.getFormId();
        String body = buildHtml(
            "🔔 Claim Pending Your Approval",
            "action",
            "A claim is awaiting your authorization.",
            form, staff,
            "Review & Approve", baseUrl + "/approval/superior/" + form.getWorkflowId()
        );
        send(superior.getEmail(), subject, body);
    }

    @Async
    public void sendApprovedToFinance(ClaimForm form, StaffInfo staff) {
        if (!enabled) { log.debug("Mail disabled - skipping Finance notification"); return; }
        userAccountRepository.findByRoleInAndIsActiveTrue(
            List.of(UserAccount.Role.FINANCE, UserAccount.Role.ADMIN)).forEach(ua -> {
            StaffInfo finStaff = staffInfoRepository.findById(ua.getStaffId()).orElse(null);
            if (finStaff != null && finStaff.getEmail() != null) {
                String body = buildHtml(
                    "✅ Claim Ready for Processing",
                    "success",
                    "A claim has been approved and is ready for finance processing.",
                    form, staff,
                    "Process Claim", baseUrl + "/finance/claims"
                );
                send(finStaff.getEmail(), "Finance: Approved Claim - Form No. " + form.getFormId(), body);
            }
        });
    }

    @Async
    public void sendRejectedToStaff(ClaimForm form, StaffInfo staff, String rejectedBy, String remarks) {
        if (!enabled) { log.debug("Mail disabled - skipping rejection email"); return; }
        if (staff == null || staff.getEmail() == null) return;

        String body = buildHtml(
            "❌ Claim Rejected",
            "danger",
            "Your claim has been rejected by " + rejectedBy + ".<br/>Reason: <em>" + nvl(remarks) + "</em>",
            form, staff,
            "View Claim", baseUrl + "/claims/" + form.getWorkflowId()
        );
        send(staff.getEmail(), "Claim Rejected - Form No. " + form.getFormId(), body);
    }

    @Async
    public void sendProcessedToStaff(ClaimForm form, StaffInfo staff) {
        if (!enabled) { log.debug("Mail disabled - skipping processed email"); return; }
        if (staff == null || staff.getEmail() == null) return;

        String body = buildHtml(
            "💰 Claim Processed",
            "success",
            "Your claim has been approved and processed by Finance.",
            form, staff,
            "Download PDF", baseUrl + "/claims/pdf/" + form.getWorkflowId()
        );
        send(staff.getEmail(), "Claim Processed - Form No. " + form.getFormId(), body);
    }

    // ── HTML email builder ────────────────────────────────────
    private String buildHtml(String heading, String type, String message,
                              ClaimForm form, StaffInfo staff, String btnLabel, String btnUrl) {
        String color = "danger".equals(type) ? "#dc2626" : "action".equals(type) ? "#2563eb" : "#059669";
        String staffName = staff != null ? staff.getName() : "Staff";
        return "<html><body style='font-family:Segoe UI,Arial,sans-serif;background:#f1f5f9;padding:40px 0;margin:0'>" +
            "<table width='600' align='center' style='background:#fff;border-radius:14px;overflow:hidden;box-shadow:0 4px 20px rgba(0,0,0,.1)'>" +
            "<tr><td style='background:#0f1523;padding:28px 36px'>" +
            "<div style='color:#fff;font-size:1.2rem;font-weight:700'>🧾 IFC E-Claims System</div></td></tr>" +
            "<tr><td style='padding:28px 36px 0'>" +
            "<div style='background:" + color + "20;color:" + color + ";padding:8px 16px;border-radius:99px;display:inline-block;font-size:.8rem;font-weight:700'>" + heading + "</div>" +
            "<p style='color:#475569;font-size:.95rem;margin:16px 0'>" + message + "</p></td></tr>" +
            "<tr><td style='padding:0 36px 16px'>" +
            "<table width='100%' style='background:#f8fafc;border-radius:10px;border:1px solid #e2e8f0'>" +
            "<tr><td style='padding:12px 18px;border-bottom:1px solid #e2e8f0'><small style='color:#64748b;text-transform:uppercase;font-size:.75rem'>Staff</small><br/><strong>" + staffName + "</strong></td>" +
            "<td style='padding:12px 18px;border-bottom:1px solid #e2e8f0'><small style='color:#64748b;text-transform:uppercase;font-size:.75rem'>Form No.</small><br/><strong>" + form.getFormId() + "</strong></td></tr>" +
            "<tr><td style='padding:12px 18px'><small style='color:#64748b;text-transform:uppercase;font-size:.75rem'>Total (RM)</small><br/><strong>" + form.getTotalClaim() + "</strong></td>" +
            "<td style='padding:12px 18px'><small style='color:#64748b;text-transform:uppercase;font-size:.75rem'>Status</small><br/><strong>" + form.getWfStatus() + "</strong></td></tr>" +
            "</table></td></tr>" +
            "<tr><td style='padding:20px 36px 32px;text-align:center'>" +
            "<a href='" + btnUrl + "' style='background:" + color + ";color:#fff;padding:11px 26px;border-radius:8px;font-weight:600;font-size:.9rem;text-decoration:none'>" + btnLabel + " →</a></td></tr>" +
            "<tr><td style='background:#f8fafc;padding:16px 36px;border-top:1px solid #e2e8f0;text-align:center'>" +
            "<small style='color:#94a3b8'>IFC E-Claims System · Automated notification · Do not reply</small></td></tr>" +
            "</table></body></html>";
    }

    private void send(String to, String subject, String html) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(msg, true, "UTF-8");
            h.setFrom(from); h.setTo(to); h.setSubject(subject); h.setText(html, true);
            mailSender.send(msg);
            log.info("Email sent to {} | {}", to, subject);
        } catch (Exception e) {
            log.error("Email failed to {} — {}", to, e.getMessage());
        }
    }

    private String nvl(String s) { return s != null ? s : "No reason provided"; }

    public void sendPasswordResetEmail(
            String recipientEmail,
            String resetLink) {

        SimpleMailMessage message = new SimpleMailMessage();

        message.setTo(recipientEmail);

        message.setSubject(
                "IFC E-Claims - Password Reset"
        );

        message.setText(
                "Dear User,\n\n" +

                        "We received a request to reset your " +
                        "IFC E-Claims password.\n\n" +

                        "Please click the link below to reset your password:\n\n" +

                        resetLink + "\n\n" +

                        "This password reset link will expire in 30 minutes.\n\n" +

                        "If you did not request a password reset, " +
                        "please ignore this email.\n\n" +

                        "Regards,\n" +
                        "IFC E-Claims"
        );

        mailSender.send(message);
    }
}
