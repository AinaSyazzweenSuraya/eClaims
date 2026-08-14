package com.eclaims.service;

import com.eclaims.dto.*;
import com.eclaims.entity.*;
import com.eclaims.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClaimService {

    @Value("${eclaims.upload.path:C:/eclaims-uploads/}")
    private String uploadPath;

    private final ClaimFormRepository     claimFormRepository;
    private final ClaimFormRowRepository  claimFormRowRepository;
    private final ClaimAttachmentRepository attachmentRepository;
    private final AuditLogRepository      auditLogRepository;
    private final StaffInfoRepository     staffInfoRepository;
    private final StaffClaimInfoRepository staffClaimInfoRepository;
    private final DepartmentRepository    departmentRepository;
    private final CompanyRepository       companyRepository;
    private final TravelLocationRepository travelLocationRepository;
    private final TravelMealRepository    travelMealRepository;
    private final ClaimTypeRepository     claimTypeRepository;
    private final CalculationService      calculationService;
    private final EmailService            emailService;

    // ================================================================
    // GENERATE IDs
    // ================================================================

    public String generateWorkflowId() {
        Integer max = claimFormRepository.findAll().stream()
            .map(c -> { try { return Integer.parseInt(c.getWorkflowId()); } catch (Exception e) { return 0; } })
            .max(Integer::compareTo).orElse(20000);
        return String.valueOf(max + 1);
    }

    public String generateFormId() {
        Integer max = claimFormRepository.findAll().stream()
            .map(c -> { try { return Integer.parseInt(c.getFormId()); } catch (Exception e) { return 0; } })
            .max(Integer::compareTo).orElse(0);
        return String.valueOf(max + 1);
    }

    // ================================================================
    // SAVE DRAFT
    // ================================================================

    @Transactional
    public ClaimForm saveDraft(String staffId, List<ClaimRowDto> rows, String existingWorkflowId) {
        ClaimForm form = null;

        if (existingWorkflowId != null && !existingWorkflowId.isBlank()) {
            form = claimFormRepository.findById(existingWorkflowId).orElse(null);
        }

        if (form == null) {
            List<ClaimForm> existing = claimFormRepository.findByStaffIdOrderByCreatedDateDesc(staffId)
                .stream().filter(c -> "DRAFT".equals(c.getWfStatus()))
                .collect(Collectors.toList());
            if (!existing.isEmpty()) form = existing.get(0);
        }

        if (form == null) {
            String wfId   = generateWorkflowId();
            String formId = generateFormId();
            StaffInfo staff = staffInfoRepository.findById(staffId).orElseThrow();
            form = ClaimForm.builder()
                .workflowId(wfId)
                .formId(formId)
                .staffId(staffId)
                .approverId(staff.getApproverId())
                .wfStatus("DRAFT")
                .totalClaim(BigDecimal.ZERO)
                .build();
            form = claimFormRepository.save(form);
        }

        String wfId = form.getWorkflowId();
        List<ClaimFormRow> existingRows = claimFormRowRepository.findByWorkflowIdOrderByCreatedDate(wfId);

        BigDecimal total = BigDecimal.ZERO;
        List<Integer> processedRowIds = new java.util.ArrayList<>();

        for (ClaimRowDto dto : rows) {
            ClaimFormRow row;

            if (dto.getRowId() != null) {
                row = claimFormRowRepository.findById(dto.getRowId()).orElse(null);
                if (row == null) {
                    row = new ClaimFormRow();
                    row.setWorkflowId(wfId);
                    row.setFormId(form.getFormId());
                    row.setCStatus("new");
                }
                if (dto.getAttachmentPath() != null && !dto.getAttachmentPath().isBlank()) {
                    if (row.getAttachmentPath() == null) {
                        row.setAttachmentPath(dto.getAttachmentPath());
                        row.setAttachmentOriginalName(dto.getAttachmentOriginalName());
                    }
                }
            } else {
                row = new ClaimFormRow();
                row.setWorkflowId(wfId);
                row.setFormId(form.getFormId());
                row.setCStatus("new");
                row.setAttachmentPath(null);
                row.setAttachmentOriginalName(null);
            }

            row.setDate(dto.getDate());
            row.setClaimId(dto.getClaimId());
            row.setDescription(dto.getDescription());
            row.setReceiptNo(dto.getReceiptNo());
            row.setProjectManagerId(dto.getProjectManagerId());
            row.setPmStatus(dto.getProjectManagerId() != null && row.getPmStatus() == null ? "Pending" : row.getPmStatus());
            row.setTimeFrom(dto.getTimeFrom());
            row.setTimeTo(dto.getTimeTo());
            row.setMedicalClinic(dto.getMedicalClinic());
            row.setPartyType(dto.getPartyType());
            row.setMileageVehicleType(dto.getMileageVehicleType());
            row.setMileageKm(dto.getMileageKm());
            row.setTravelId(dto.getTravelId());
            row.setMealId(dto.getMealId());
            row.setAmount(dto.getAmount());
            row.setTotal(dto.getTotal());

            claimFormRowRepository.save(row);
            if (row.getRowId() != null) processedRowIds.add(row.getRowId());
            if (dto.getTotal() != null) total = total.add(dto.getTotal());
        }

        existingRows.stream()
            .filter(r -> r.getRowId() != null && !processedRowIds.contains(r.getRowId()))
            .forEach(r -> claimFormRowRepository.deleteById(r.getRowId()));

        form.setTotalClaim(total);
        claimFormRepository.save(form);

        auditLogRepository.save(AuditLog.builder()
            .workflowId(form.getWorkflowId()).formId(form.getFormId())
            .staffId(staffId).status("DRAFT").remarks("Saved as draft").build());

        return form;
    }

    // ================================================================
    // SUBMIT CLAIM
    // ================================================================

    @Transactional
    public ClaimForm submitClaim(String workflowId, String staffId) {
        ClaimForm form = claimFormRepository.findById(workflowId)
            .orElseThrow(() -> new IllegalArgumentException("Claim not found"));

        // Allow submit from DRAFT or rejected states
        String currentStatus = form.getWfStatus();
        boolean canSubmit = "DRAFT".equals(currentStatus)
                         || "PM_REJECTED".equals(currentStatus)
                         || "SUPERIOR_REJECTED".equals(currentStatus);
        if (!canSubmit)
            throw new IllegalStateException("Only DRAFT or rejected claims can be submitted");

        boolean isResubmit = "PM_REJECTED".equals(currentStatus)
                           || "SUPERIOR_REJECTED".equals(currentStatus);

        form.setWfStatus("SUBMITTED");
        claimFormRepository.save(form);

        String auditRemark = isResubmit ? "Claim resubmitted after rejection" : "Claim submitted";
        auditLogRepository.save(AuditLog.builder()
            .workflowId(workflowId).formId(form.getFormId())
            .staffId(staffId).status("SUBMITTED").remarks(auditRemark).build());

        List<ClaimFormRow> rows = claimFormRowRepository.findByWorkflowIdOrderByCreatedDate(workflowId);

        // If resubmitting, reset PM row statuses back to Pending
        if (isResubmit) {
            rows.stream()
                .filter(r -> r.getProjectManagerId() != null && !r.getProjectManagerId().isBlank())
                .forEach(r -> {
                    r.setPmStatus("Pending");
                    claimFormRowRepository.save(r);
                });
        }

        Set<String> pmIds = rows.stream()
            .filter(r -> r.getProjectManagerId() != null && !r.getProjectManagerId().isBlank())
            .map(ClaimFormRow::getProjectManagerId)
            .collect(Collectors.toSet());

        StaffInfo staff = staffInfoRepository.findById(staffId).orElse(null);
        pmIds.forEach(pmId -> emailService.sendSubmittedToPm(form, staff, pmId));

        if (pmIds.isEmpty()) {
            emailService.sendSubmittedToSuperior(form, staff);
        }

        return form;
    }

    // ================================================================
    // PM APPROVE/REJECT
    // ================================================================

    @Transactional
    public void pmAction(String workflowId, String pmId, String action, String remarks) {
        ClaimForm form = claimFormRepository.findById(workflowId)
            .orElseThrow(() -> new IllegalArgumentException("Claim not found"));

        List<ClaimFormRow> pmRows = claimFormRowRepository
            .findByWorkflowIdAndProjectManagerId(workflowId, pmId);

        String newPmStatus = "Approve".equalsIgnoreCase(action) ? "Approved" : "Rejected";
        pmRows.forEach(r -> {
            r.setPmStatus(newPmStatus);
            claimFormRowRepository.save(r);
        });

        auditLogRepository.save(AuditLog.builder()
            .workflowId(workflowId).formId(form.getFormId())
            .projectManagerId(pmId).status("PM_" + newPmStatus.toUpperCase())
            .remarks(remarks).build());

        List<ClaimFormRow> allRows = claimFormRowRepository.findByWorkflowIdOrderByCreatedDate(workflowId);
        boolean anyRejected = allRows.stream().anyMatch(r -> "Rejected".equals(r.getPmStatus()));
        boolean allActioned = allRows.stream()
            .filter(r -> r.getProjectManagerId() != null && !r.getProjectManagerId().isBlank())
            .allMatch(r -> "Approved".equals(r.getPmStatus()) || "Rejected".equals(r.getPmStatus()));

        if (anyRejected) {
            form.setWfStatus("PM_REJECTED");
            claimFormRepository.save(form);
            StaffInfo staff = staffInfoRepository.findById(form.getStaffId()).orElse(null);
            emailService.sendRejectedToStaff(form, staff, "PM", remarks);
        } else if (allActioned) {
            form.setWfStatus("PM_APPROVED");
            claimFormRepository.save(form);
            StaffInfo staff = staffInfoRepository.findById(form.getStaffId()).orElse(null);
            emailService.sendSubmittedToSuperior(form, staff);
        }
    }

    // ================================================================
    // SUPERIOR APPROVE/REJECT
    // ================================================================

    @Transactional
    public void superiorAction(String workflowId, String superiorId, String action, String remarks) {
        ClaimForm form = claimFormRepository.findById(workflowId)
            .orElseThrow(() -> new IllegalArgumentException("Claim not found"));

        StaffInfo staff = staffInfoRepository.findById(form.getStaffId()).orElse(null);

        if ("Approve".equalsIgnoreCase(action)) {
            form.setWfStatus("SUPERIOR_APPROVED");
            claimFormRepository.save(form);
            auditLogRepository.save(AuditLog.builder()
                .workflowId(workflowId).formId(form.getFormId())
                .approverId(superiorId).status("SUPERIOR_APPROVED").remarks(remarks).build());
            emailService.sendApprovedToFinance(form, staff);
        } else {
            form.setWfStatus("SUPERIOR_REJECTED");
            claimFormRepository.save(form);
            auditLogRepository.save(AuditLog.builder()
                .workflowId(workflowId).formId(form.getFormId())
                .approverId(superiorId).status("SUPERIOR_REJECTED").remarks(remarks).build());
            emailService.sendRejectedToStaff(form, staff, "Authorized Superior", remarks);
        }
    }

    // ================================================================
    // FINANCE PROCESS
    // ================================================================

    @Transactional
    public void financeProcess(String workflowId, String financeId, String remarks) {
        ClaimForm form = claimFormRepository.findById(workflowId)
            .orElseThrow(() -> new IllegalArgumentException("Claim not found"));

        form.setWfStatus("FINANCE_PROCESSED");
        claimFormRepository.save(form);

        auditLogRepository.save(AuditLog.builder()
            .workflowId(workflowId).formId(form.getFormId())
            .finance(financeId).status("FINANCE_PROCESSED").remarks(remarks).build());

        updateClaimBalances(form);

        StaffInfo staff = staffInfoRepository.findById(form.getStaffId()).orElse(null);
        emailService.sendProcessedToStaff(form, staff);
    }

    // ================================================================
    // UPDATE CLAIM BALANCES
    // ================================================================

    @Transactional
    public void updateClaimBalances(ClaimForm form) {
        StaffClaimInfo info = staffClaimInfoRepository.findById(form.getStaffId()).orElse(null);
        if (info == null) return;

        List<ClaimFormRow> rows = claimFormRowRepository.findByWorkflowIdOrderByCreatedDate(form.getWorkflowId());
        Map<String, BigDecimal> totals = new HashMap<>();
        rows.forEach(r -> totals.merge(r.getClaimId(), safe(r.getTotal()), BigDecimal::add));

        totals.forEach((claimId, total) -> {
            switch (claimId) {
                case "CL01" -> { info.setMealWeekdaysClaimed(safe(info.getMealWeekdaysClaimed()).add(total));
                                 info.setMealWeekdaysBalance(safe(info.getMealWeekdaysEntitled()).subtract(info.getMealWeekdaysClaimed())); }
                case "CL02" -> { info.setMealHolidayClaimed(safe(info.getMealHolidayClaimed()).add(total));
                                 info.setMealHolidayBalance(safe(info.getMealHolidayEntitled()).subtract(info.getMealHolidayClaimed())); }
                case "CL03" -> { info.setPhoneClaimed(safe(info.getPhoneClaimed()).add(total));
                                 info.setPhoneBalance(safe(info.getPhoneEntitled()).subtract(info.getPhoneClaimed())); }
                case "CL04" -> { info.setMileageClaimed(safe(info.getMileageClaimed()).add(total));
                                 info.setMileageBalance(safe(info.getMileageEntitled()).subtract(info.getMileageClaimed())); }
                case "CL05" -> { info.setPanelMedicalClaimed(safe(info.getPanelMedicalClaimed()).add(total));
                                 info.setPanelMedicalBalance(safe(info.getPanelMedicalEntitled()).subtract(info.getPanelMedicalClaimed())); }
                case "CL06" -> { info.setNonPanelMedicalClaimed(safe(info.getNonPanelMedicalClaimed()).add(total));
                                 info.setNonPanelMedicalBalance(safe(info.getNonPanelMedicalEntitled()).subtract(info.getNonPanelMedicalClaimed())); }
                case "CL07" -> { info.setDentalOpticalClaimed(safe(info.getDentalOpticalClaimed()).add(total));
                                 info.setDentalOpticalBalance(safe(info.getDentalOpticalEntitled()).subtract(info.getDentalOpticalClaimed())); }
                case "CL08" -> { info.setPetrolClaimed(safe(info.getPetrolClaimed()).add(total));
                                 info.setPetrolBalance(safe(info.getPetrolEntitled()).subtract(info.getPetrolClaimed())); }
                case "CL09" -> { info.setBizEntClaimed(safe(info.getBizEntClaimed()).add(total));
                                 info.setBizEntBalance(safe(info.getBizEntEntitled()).subtract(info.getBizEntClaimed())); }
                case "CL10" -> { info.setTollClaimed(safe(info.getTollClaimed()).add(total));
                                 info.setTollBalance(safe(info.getTollEntitled()).subtract(info.getTollClaimed())); }
                case "CL11" -> { info.setOthersClaimed(safe(info.getOthersClaimed()).add(total));
                                 info.setOthersBalance(safe(info.getOthersEntitled()).subtract(info.getOthersClaimed())); }
                case "CL12" -> { info.setTravelClaimed(safe(info.getTravelClaimed()).add(total));
                                 info.setTravelBalance(safe(info.getTravelEntitled()).subtract(info.getTravelClaimed())); }
                case "CL13" -> { info.setTravelClaimed(safe(info.getTravelClaimed()).add(total));
                				 info.setTravelBalance(safe(info.getTravelEntitled()).subtract(info.getTravelClaimed())); }
                case "CL14" -> { info.setTravelClaimed(safe(info.getTravelClaimed()).add(total));
                				 info.setTravelBalance(safe(info.getTravelEntitled()).subtract(info.getTravelClaimed())); }
            }
        });
        staffClaimInfoRepository.save(info);
    }

    // ================================================================
    // LOAD FORM + BALANCE DATA
    // ================================================================

    public ClaimFormDto loadForm(String workflowId) {
        ClaimForm form = claimFormRepository.findById(workflowId)
            .orElseThrow(() -> new IllegalArgumentException("Claim not found"));
        return toDto(form);
    }

    public List<ClaimFormDto> getClaimsForStaff(String staffId) {
        return claimFormRepository.findByStaffIdOrderByCreatedDateDesc(staffId)
            .stream().map(this::toDto).collect(Collectors.toList());
    }

    public List<ClaimFormDto> getAllClaims() {
        return claimFormRepository.findAllOrderByDateDesc()
            .stream().map(this::toDto).collect(Collectors.toList());
    }

    public List<ClaimFormDto> getClaimsForSuperior(String approverId) {
        List<String> statuses = List.of("SUBMITTED", "PM_APPROVED");
        return claimFormRepository
            .findByApproverIdAndWfStatusInOrderByCreatedDateDesc(approverId, statuses)
            .stream().map(this::toDto).collect(Collectors.toList());
    }

    public List<ClaimFormDto> getPendingForFinance() {
        return claimFormRepository
            .findByWfStatusOrderByCreatedDateDesc("SUPERIOR_APPROVED")
            .stream().map(this::toDto).collect(Collectors.toList());
    }

    public List<StaffClaimBalanceDto> getBalances(String staffId) {
        StaffClaimInfo info = staffClaimInfoRepository.findById(staffId).orElse(null);
        if (info == null) return Collections.emptyList();

        List<ClaimType> types = claimTypeRepository.findAllByOrderByClaimId();
        return types.stream().map(t -> {
            BigDecimal ent = BigDecimal.ZERO, claimed = BigDecimal.ZERO, bal = BigDecimal.ZERO;
            switch (t.getClaimId()) {
                case "CL01" -> { ent=safe(info.getMealWeekdaysEntitled()); claimed=safe(info.getMealWeekdaysClaimed()); bal=safe(info.getMealWeekdaysBalance()); }
                case "CL02" -> { ent=safe(info.getMealHolidayEntitled());  claimed=safe(info.getMealHolidayClaimed());  bal=safe(info.getMealHolidayBalance()); }
                case "CL03" -> { ent=safe(info.getPhoneEntitled());        claimed=safe(info.getPhoneClaimed());        bal=safe(info.getPhoneBalance()); }
                case "CL04" -> { ent=safe(info.getMileageEntitled());      claimed=safe(info.getMileageClaimed());      bal=safe(info.getMileageBalance()); }
                case "CL05" -> { ent=safe(info.getPanelMedicalEntitled()); claimed=safe(info.getPanelMedicalClaimed()); bal=safe(info.getPanelMedicalBalance()); }
                case "CL06" -> { ent=safe(info.getNonPanelMedicalEntitled());claimed=safe(info.getNonPanelMedicalClaimed());bal=safe(info.getNonPanelMedicalBalance()); }
                case "CL07" -> { ent=safe(info.getDentalOpticalEntitled()); claimed=safe(info.getDentalOpticalClaimed()); bal=safe(info.getDentalOpticalBalance()); }
                case "CL08" -> { ent=safe(info.getPetrolEntitled());       claimed=safe(info.getPetrolClaimed());        bal=safe(info.getPetrolBalance()); }
                case "CL09" -> { ent=safe(info.getBizEntEntitled());       claimed=safe(info.getBizEntClaimed());        bal=safe(info.getBizEntBalance()); }
                case "CL10" -> { ent=safe(info.getTollEntitled());         claimed=safe(info.getTollClaimed());          bal=safe(info.getTollBalance()); }
                case "CL11" -> { ent=safe(info.getOthersEntitled());       claimed=safe(info.getOthersClaimed());        bal=safe(info.getOthersBalance()); }
                case "CL12" -> { ent=safe(info.getTravelEntitled());       claimed=safe(info.getTravelClaimed());        bal=safe(info.getTravelBalance()); }
                case "CL13" -> { ent=safe(info.getTravelEntitled());       claimed=safe(info.getTravelClaimed());        bal=safe(info.getTravelBalance()); }
                case "CL14" -> { ent=safe(info.getTravelEntitled());       claimed=safe(info.getTravelClaimed());        bal=safe(info.getTravelBalance()); }
            }
            return StaffClaimBalanceDto.builder()
                .claimId(t.getClaimId()).claimTitle(t.getClaimTitle())
                .entitled(ent).claimed(claimed).balance(bal).build();
        }).collect(Collectors.toList());
    }

    // ================================================================
    // HELPERS
    // ================================================================

    private ClaimFormDto toDto(ClaimForm form) {
        StaffInfo staff = staffInfoRepository.findById(form.getStaffId()).orElse(null);
        String deptName = "";
        String compName = "";
        if (staff != null) {
            deptName = departmentRepository.findById(staff.getDepartmentId()).map(d -> d.getDepartmentName()).orElse("");
            compName = companyRepository.findById(staff.getCompanyId()).map(c -> c.getCompanyName()).orElse("");
        }
        List<ClaimFormRow> rows = claimFormRowRepository.findByWorkflowIdOrderByCreatedDate(form.getWorkflowId());
        List<ClaimRowDto> rowDtos = rows.stream().map(r -> ClaimRowDto.builder()
            .rowId(r.getRowId()).date(r.getDate()).claimId(r.getClaimId())
            .description(r.getDescription()).projectManagerId(r.getProjectManagerId())
            .receiptNo(r.getReceiptNo())
            .timeFrom(r.getTimeFrom()).timeTo(r.getTimeTo())
            .medicalClinic(r.getMedicalClinic())
            .partyType(r.getPartyType())
            .mileageVehicleType(r.getMileageVehicleType()).mileageKm(r.getMileageKm())
            .travelId(r.getTravelId()).mealId(r.getMealId())
            .amount(r.getAmount()).total(r.getTotal())
            .attachmentPath(r.getAttachmentPath())
            .attachmentOriginalName(r.getAttachmentOriginalName())
            .hasAttachment(r.getAttachmentPath() != null && !r.getAttachmentPath().isBlank())
            .build()).collect(Collectors.toList());

        // Allow edit for DRAFT and rejected claims
        boolean canEdit = "DRAFT".equals(form.getWfStatus())
                       || "PM_REJECTED".equals(form.getWfStatus())
                       || "SUPERIOR_REJECTED".equals(form.getWfStatus());

        // Look up approver name from StaffInfo
        String approverName = "";
        if (form.getApproverId() != null && !form.getApproverId().isBlank()) {
            approverName = staffInfoRepository.findById(form.getApproverId())
                .map(StaffInfo::getName)
                .orElse(form.getApproverId());
        }

        return ClaimFormDto.builder()
            .workflowId(form.getWorkflowId()).formId(form.getFormId())
            .staffId(form.getStaffId())
            .staffName(staff != null ? staff.getName() : "")
            .designation(staff != null ? staff.getDesignation() : "")
            .department(deptName).company(compName)
            .approverId(form.getApproverId())
            .approverName(approverName)
            .wfStatus(form.getWfStatus())
            .totalClaim(form.getTotalClaim())
            .createdDate(form.getCreatedDate())
            .rows(rowDtos).readOnly(!canEdit).canEdit(canEdit)
            .build();
    }

    private BigDecimal safe(BigDecimal v) { return v != null ? v : BigDecimal.ZERO; }

    // ================================================================
    // DELETE DRAFT
    // ================================================================

    @Transactional
    public void deleteDraft(String workflowId, String requestingStaffId) {
        ClaimForm form = claimFormRepository.findById(workflowId)
            .orElseThrow(() -> new IllegalArgumentException("Claim not found: " + workflowId));

        if (!"DRAFT".equals(form.getWfStatus()))
            throw new IllegalStateException("Only DRAFT claims can be deleted.");

        if (!form.getStaffId().equals(requestingStaffId))
            throw new IllegalStateException("You can only delete your own claims.");

        List<ClaimFormRow> rows = claimFormRowRepository.findByWorkflowIdOrderByCreatedDate(workflowId);
        rows.stream()
            .filter(r -> r.getAttachmentPath() != null && !r.getAttachmentPath().isBlank())
            .forEach(r -> deletePhysicalFile(r.getAttachmentPath()));

        attachmentRepository.findByWorkflowIdOrderByDocDate(workflowId).stream()
            .filter(a -> a.getStoredFileName() != null && !a.getStoredFileName().isBlank())
            .forEach(a -> deletePhysicalFile(a.getStoredFileName()));

        claimFormRowRepository.deleteByWorkflowId(workflowId);
        attachmentRepository.deleteByWorkflowId(workflowId);
        auditLogRepository.deleteByWorkflowId(workflowId);
        claimFormRepository.deleteById(workflowId);

        log.info("Draft deleted: workflowId={} by staffId={}", workflowId, requestingStaffId);
    }

    private void deletePhysicalFile(String storedPath) {
        try {
            java.nio.file.Path path = java.nio.file.Paths.get(uploadPath, storedPath);
            java.nio.file.Files.deleteIfExists(path);
        } catch (Exception e) {
            log.warn("Could not delete file: {}", storedPath);
        }
    }
}
