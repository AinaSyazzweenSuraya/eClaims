package com.eclaims.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.eclaims.dto.ClaimFormDto;
import com.eclaims.dto.ClaimRowDto;
import com.eclaims.dto.StaffClaimBalanceDto;
import com.eclaims.entity.AuditLog;
import com.eclaims.entity.ClaimForm;
import com.eclaims.entity.StaffInfo;
import com.eclaims.entity.TravelLocation;
import com.eclaims.entity.TravelMeal;
import com.eclaims.repository.AuditLogRepository;
import com.eclaims.repository.ClaimFormRowRepository;
import com.eclaims.repository.ClaimTypeRepository;
import com.eclaims.repository.PanelMedicalRepository;
import com.eclaims.repository.ProjectManagerRepository;
import com.eclaims.repository.TravelLocationRepository;
import com.eclaims.repository.TravelMealRepository;
import com.eclaims.service.AttachmentService;
import com.eclaims.service.CalculationService;
import com.eclaims.service.ClaimService;
import com.eclaims.service.PdfService;
import com.eclaims.service.UserService;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/claims")
@RequiredArgsConstructor
public class ClaimController {

    private final ClaimService              claimService;
    private final UserService               userService;
    private final AttachmentService         attachmentService;
    private final PdfService                pdfService;
    private final CalculationService        calcService;
    private final AuditLogRepository        auditLogRepository;
    private final com.eclaims.config.FeatureConfig featureConfig;
    private final ClaimFormRowRepository    claimFormRowRepository;
    private final ClaimTypeRepository       claimTypeRepository;
    private final PanelMedicalRepository    panelMedicalRepository;
    private final TravelLocationRepository  travelLocationRepository;
    private final TravelMealRepository      travelMealRepository;
    private final ProjectManagerRepository  pmRepository;

    // ── New claim form ──────────────────────────────────────────
    @GetMapping("/new")
    public String newClaim(@AuthenticationPrincipal UserDetails user,
                            org.springframework.web.servlet.mvc.support.RedirectAttributes ra,
                            Model model) {
        StaffInfo staff = userService.getStaffByUsername(user.getUsername());

        // Check if staff already has an existing DRAFT
        List<com.eclaims.dto.ClaimFormDto> claims = claimService.getClaimsForStaff(staff.getStaffId());
        java.util.Optional<com.eclaims.dto.ClaimFormDto> existingDraft = claims.stream()
            .filter(c -> "DRAFT".equals(c.getWfStatus()))
            .findFirst();

        if (existingDraft.isPresent()) {
            com.eclaims.dto.ClaimFormDto draft = existingDraft.get();
            ra.addFlashAttribute("draftWarning", true);
            ra.addFlashAttribute("draftWorkflowId", draft.getWorkflowId());
            ra.addFlashAttribute("draftFormId",     draft.getFormId());
            return "redirect:/dashboard";
        }

        addFormModel(model, null, staff);
        model.addAttribute("activePage", "newClaim");
        return "claims/form";
    }

    // ── Edit draft ──────────────────────────────────────────────
    @GetMapping("/{workflowId}/edit")
    public String editClaim(@PathVariable String workflowId,
                             @AuthenticationPrincipal UserDetails user, Model model) {
        StaffInfo staff = userService.getStaffByUsername(user.getUsername());
        ClaimFormDto form = claimService.loadForm(workflowId);
        if (!form.getStaffId().equals(staff.getStaffId()))
            return "redirect:/dashboard";
        addFormModel(model, form, staff);
        model.addAttribute("activePage", "claims");
        return "claims/form";
    }

    // ── View claim ──────────────────────────────────────────────
    @GetMapping("/{workflowId}")
    public String viewClaim(@PathVariable String workflowId,
                             @AuthenticationPrincipal UserDetails user, Model model) {
        StaffInfo staff = userService.getStaffByUsername(user.getUsername());
        ClaimFormDto form = claimService.loadForm(workflowId);

        // Ownership check — STAFF can only view their own claims
        if (!canAccess(form.getStaffId(), user)) return "redirect:/dashboard";

        List<StaffClaimBalanceDto> balances = claimService.getBalances(staff.getStaffId());
        List<AuditLog> auditLogs = auditLogRepository.findByWorkflowIdOrderByCreatedDate(workflowId);
        model.addAttribute("form",       form);
        model.addAttribute("staff",      staff);
        model.addAttribute("balances",   balances);
        model.addAttribute("auditLogs",  auditLogs);
        model.addAttribute("claimTypes",
            claimTypeRepository.findAllByOrderByClaimId().stream()
                .map(c -> Map.of("claimId", (Object)c.getClaimId(), "claimTitle", c.getClaimTitle() != null ? c.getClaimTitle() : c.getClaimId()))
                .toList());
        model.addAttribute("activePage", "claims");
        return "claims/view";
    }

    // ── Save draft (AJAX) ───────────────────────────────────────
    @PostMapping("/save-draft")
    @ResponseBody
    public ResponseEntity<?> saveDraft(@RequestBody Map<String, Object> payload,
                                        @AuthenticationPrincipal UserDetails user) {
        try {
            StaffInfo staff = userService.getStaffByUsername(user.getUsername());
            List<ClaimRowDto> rows = parseRows(payload);
            String existingWorkflowId = payload.get("workflowId") != null
                ? payload.get("workflowId").toString() : null;
            ClaimForm form = claimService.saveDraft(staff.getStaffId(), rows, existingWorkflowId);
            return ResponseEntity.ok(Map.of(
                "success",    true,
                "workflowId", form.getWorkflowId(),
                "formId",     form.getFormId(),
                "message",    "Draft saved"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // ── Submit claim (AJAX) ─────────────────────────────────────
    @PostMapping("/{workflowId}/submit")
    @ResponseBody
    public ResponseEntity<?> submitClaim(@PathVariable String workflowId,
                                          @AuthenticationPrincipal UserDetails user) {
        try {
            StaffInfo staff = userService.getStaffByUsername(user.getUsername());
            ClaimFormDto form = claimService.loadForm(workflowId);

            // Ownership check
            if (!canAccess(form.getStaffId(), user))
                return ResponseEntity.status(403).body(Map.of("success", false, "message", "Access denied"));

            claimService.submitClaim(workflowId, staff.getStaffId());
            return ResponseEntity.ok(Map.of(
                "success",     true,
                "message",     "Claim submitted successfully",
                "redirectUrl", "/claims/" + workflowId
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // ── Upload row attachment ───────────────────────────────────
    @PostMapping("/row/{rowId}/attachment")
    @ResponseBody
    public ResponseEntity<?> uploadRowAttachment(@PathVariable Integer rowId,
                                                  @RequestParam("file") MultipartFile file) {
        try {
            attachmentService.uploadRowAttachment(rowId, file);
            return ResponseEntity.ok(Map.of("success", true, "message", "Attachment uploaded"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // ── Upload form-level attachment ────────────────────────────
    @PostMapping("/{workflowId}/attachment")
    @ResponseBody
    public ResponseEntity<?> uploadFormAttachment(@PathVariable String workflowId,
                                                   @RequestParam("file") MultipartFile file,
                                                   @RequestParam(required = false) String formId,
                                                   @AuthenticationPrincipal UserDetails user) {
        try {
            ClaimFormDto form = claimService.loadForm(workflowId);
            if (!canAccess(form.getStaffId(), user))
                return ResponseEntity.status(403).body(Map.of("success", false, "message", "Access denied"));

            attachmentService.uploadFormAttachment(workflowId, formId != null ? formId : "", file);
            return ResponseEntity.ok(Map.of("success", true, "message", "File uploaded"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // ── Download attachment ─────────────────────────────────────
    @GetMapping("/attachment/download/{stored:.+}")
    public void downloadAttachment(@PathVariable String stored,
                                    jakarta.servlet.http.HttpServletResponse response) throws Exception {
        java.nio.file.Path path = attachmentService.getFilePath(stored);
        response.setContentType("application/octet-stream");
        response.setHeader("Content-Disposition",
            "attachment; filename=\"" + path.getFileName().toString() + "\"");
        java.nio.file.Files.copy(path, response.getOutputStream());
    }

    // ── Download full merged PDF ────────────────────────────────
    @GetMapping("/pdf/{workflowId}")
    public void downloadPdf(@PathVariable String workflowId,
                             @AuthenticationPrincipal UserDetails user,
                             jakarta.servlet.http.HttpServletResponse response) throws Exception {
        ClaimFormDto form = claimService.loadForm(workflowId);

        // Ownership check
        if (!canAccess(form.getStaffId(), user)) {
            response.sendError(403, "Access denied");
            return;
        }

        byte[] pdf = pdfService.generateFullPdf(workflowId);
        String filename = "EClaim_Form" + form.getFormId() + "_" + form.getStaffId() + ".pdf";
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        response.getOutputStream().write(pdf);
    }

    // ── Download summary PDF (no attachments) ──────────────────
    @GetMapping("/pdf-summary/{workflowId}")
    public void downloadSummaryPdf(@PathVariable String workflowId,
                                    @AuthenticationPrincipal UserDetails user,
                                    jakarta.servlet.http.HttpServletResponse response) throws Exception {
        ClaimFormDto form = claimService.loadForm(workflowId);
        if (!canAccess(form.getStaffId(), user)) { response.sendError(403, "Access denied"); return; }
        byte[] pdf = pdfService.generateSummaryOnlyPdf(workflowId);
        String filename = "EClaim_Summary_Form" + form.getFormId() + "_" + form.getStaffId() + ".pdf";
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        response.getOutputStream().write(pdf);
    }

    // ── AJAX: calculate meal ────────────────────────────────────
    @GetMapping("/api/calc/meal")
    @ResponseBody
    public ResponseEntity<?> calcMeal(@RequestParam String claimId,
                                       @RequestParam String timeFrom,
                                       @RequestParam String timeTo) {
        BigDecimal hours = calcService.calcHours(timeFrom, timeTo);
        BigDecimal total = "CL01".equals(claimId)
            ? calcService.calcMealWeekday(hours)
            : calcService.calcMealHoliday(hours);
        return ResponseEntity.ok(Map.of("hours", hours, "total", total));
    }

    // ── AJAX: calculate mileage ─────────────────────────────────
    @GetMapping("/api/calc/mileage")
    @ResponseBody
    public ResponseEntity<?> calcMileage(@RequestParam String vehicleType,
                                          @RequestParam Integer km) {
        BigDecimal total = calcService.calcMileage(vehicleType, km);
        return ResponseEntity.ok(Map.of("total", total));
    }

    // ── AJAX: calculate travel ──────────────────────────────────
    @GetMapping("/api/calc/travel")
    @ResponseBody
    public ResponseEntity<?> calcTravel(@RequestParam String travelId,
                                         @RequestParam String mealId) {
        try {
            TravelLocation loc  = travelLocationRepository.findById(travelId).orElseThrow();
            TravelMeal     meal = travelMealRepository.findById(mealId).orElseThrow();
            BigDecimal total = calcService.calcTravel(loc.getClaimLimit(), meal.getPercentageAllowance());
            return ResponseEntity.ok(Map.of(
                "claimLimit", loc.getClaimLimit(),
                "percentage", meal.getPercentageAllowance(),
                "total",      total
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    // ── Delete draft ────────────────────────────────────────────
    @PostMapping("/{workflowId}/delete")
    public String deleteDraft(@PathVariable String workflowId,
                               @AuthenticationPrincipal UserDetails user,
                               RedirectAttributes ra) {
        try {
            StaffInfo staff = userService.getStaffByUsername(user.getUsername());
            ClaimFormDto form = claimService.loadForm(workflowId);

            // Ownership check — only owner or privileged roles can delete
            if (!canAccess(form.getStaffId(), user)) {
                ra.addFlashAttribute("errorMsg", "Access denied.");
                return "redirect:/dashboard";
            }

            // Admin can delete any draft, staff can only delete their own
            String staffId = staff.getStaffId();
            boolean isPrivileged = user.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            if (isPrivileged) {
                // Admin bypass — use form's staffId so ownership check inside service passes
                staffId = form.getStaffId();
            }

            claimService.deleteDraft(workflowId, staffId);
            ra.addFlashAttribute("successMsg", "Draft claim deleted successfully.");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/dashboard";
    }

    // ── AJAX: get saved rows with rowIds (used after save to assign rowIds to DOM) ──
    @GetMapping("/api/rows/{workflowId}")
    @ResponseBody
    public ResponseEntity<?> getSavedRows(@PathVariable String workflowId,
                                           @AuthenticationPrincipal UserDetails user) {
        try {
            ClaimFormDto form = claimService.loadForm(workflowId);
            if (!canAccess(form.getStaffId(), user))
                return ResponseEntity.status(403).body(Map.of("error", "Access denied"));

            List<Map<String, Object>> rows = claimFormRowRepository
                .findByWorkflowIdOrderByCreatedDate(workflowId)
                .stream()
                .map(r -> {
                    Map<String, Object> m = new java.util.HashMap<>();
                    m.put("rowId",   r.getRowId());
                    m.put("claimId", r.getClaimId());
                    return m;
                })
                .toList();
            return ResponseEntity.ok(rows);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── OWNERSHIP CHECK ──────────────────────────────────────────
    /**
     * Checks if the current user is allowed to access a claim.
     *
     * Rules (without disturbing existing logic):
     * - ADMIN, FINANCE, SUPERIOR, MANAGER → can access any claim (needed for approvals)
     * - STAFF → can only access their own claims
     *
     * Returns true if access is allowed, false if denied.
     */
    private boolean canAccess(String claimStaffId, UserDetails user) {
        // Check role — non-STAFF roles can see all claims
        boolean isPrivileged = user.getAuthorities().stream()
            .anyMatch(a -> {
                String role = a.getAuthority();
                return role.equals("ROLE_ADMIN")
                    || role.equals("ROLE_FINANCE")
                    || role.equals("ROLE_SUPERIOR")
                    || role.equals("ROLE_MANAGER");
            });
        if (isPrivileged) return true;

        // STAFF role — only their own claims
        StaffInfo staff = userService.getStaffByUsername(user.getUsername());
        return staff != null && staff.getStaffId().equals(claimStaffId);
    }

    // ── HELPERS ─────────────────────────────────────────────────

    private void addFormModel(Model model, ClaimFormDto form, StaffInfo staff) {
        model.addAttribute("form",   form);
        model.addAttribute("ocrEnabled", featureConfig.isOcrEnabled());
        model.addAttribute("staff",  staff);
        model.addAttribute("claimTypes",
            claimTypeRepository.findAllByOrderByClaimId().stream()
                .map(c -> Map.of("claimId", (Object)c.getClaimId(), "claimTitle", c.getClaimTitle() != null ? c.getClaimTitle() : c.getClaimId()))
                .toList());
        model.addAttribute("panelClinics",
            panelMedicalRepository.findByIsActiveTrueOrderByMedicalClinic().stream()
                .map(p -> Map.of("id", (Object)p.getPanelMedicalId(), "name", p.getMedicalClinic()))
                .toList());
        model.addAttribute("travelLocations",
            travelLocationRepository.findAllByOrderByLocation().stream()
                .map(t -> Map.of("id", (Object)t.getTravelId(), "location", t.getLocation(),
                                 "limit", t.getClaimLimit() != null ? t.getClaimLimit() : BigDecimal.ZERO))
                .toList());
        model.addAttribute("travelMeals",
            travelMealRepository.findAllByOrderByDescription().stream()
                .map(m -> Map.of("id", (Object)m.getMealId(), "desc", m.getDescription(),
                                 "pct", m.getPercentageAllowance() != null ? m.getPercentageAllowance() : "0"))
                .toList());
        model.addAttribute("projectManagers",
            pmRepository.findAllByOrderByManagerName().stream()
                .filter(p -> p.getStaffId() != null && !p.getStaffId().isBlank())
                .map(p -> Map.of("id", (Object)p.getStaffId(), "name", p.getManagerName()))
                .toList());
        if (form != null && form.getStaffId() != null)
            model.addAttribute("balances", claimService.getBalances(form.getStaffId()));
        else if (staff != null)
            model.addAttribute("balances", claimService.getBalances(staff.getStaffId()));
    }

    @SuppressWarnings("unchecked")
    private List<ClaimRowDto> parseRows(Map<String, Object> payload) {
        Object rawObj = payload.get("rows");
        if (!(rawObj instanceof List)) return List.of();
        List<Map<String, Object>> rawRows = (List<Map<String, Object>>) rawObj;
        return rawRows.stream().map(r -> {
            ClaimRowDto dto = new ClaimRowDto();
            dto.setClaimId(str(r, "claimId"));
            dto.setDescription(str(r, "description"));
            dto.setProjectManagerId(str(r, "projectManagerId"));
            dto.setTimeFrom(str(r, "timeFrom"));
            dto.setTimeTo(str(r, "timeTo"));
            dto.setMedicalClinic(str(r, "medicalClinic"));
            dto.setMileageVehicleType(str(r, "mileageVehicleType"));
            dto.setTravelId(str(r, "travelId"));
            dto.setMealId(str(r, "mealId"));
            dto.setAttachmentPath(str(r, "attachmentPath"));
            dto.setAttachmentOriginalName(str(r, "attachmentOriginalName"));
            if (r.get("date") != null)
                try { dto.setDate(LocalDate.parse(r.get("date").toString())); } catch (Exception ignored) {}
            if (r.get("amount") != null)
                try { dto.setAmount(new BigDecimal(r.get("amount").toString())); } catch (Exception ignored) {}
            if (r.get("total") != null)
                try { dto.setTotal(new BigDecimal(r.get("total").toString())); } catch (Exception ignored) {}
            if (r.get("mileageKm") != null)
                try { dto.setMileageKm(Integer.parseInt(r.get("mileageKm").toString())); } catch (Exception ignored) {}
            if (r.get("rowId") != null)
                try { dto.setRowId(Integer.parseInt(r.get("rowId").toString())); } catch (Exception ignored) {}
            return dto;
        }).toList();
    }

    private String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return (v != null && !v.toString().isBlank()) ? v.toString() : null;
    }
}
