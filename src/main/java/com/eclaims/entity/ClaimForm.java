package com.eclaims.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "EN_TBL_DATA_CLAIM_FORM")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClaimForm {

    @Id
    @Column(name = "WorkflowID", length = 50)
    private String workflowId;

    @Column(name = "FormID", length = 50)
    private String formId;

    @Column(name = "StaffID", length = 50)
    private String staffId;

    @Column(name = "ApproverID", length = 50)
    private String approverId;

    // Store as plain String to handle legacy values like 'Pending', 'new', 'Approved' etc.
    // from old Liquid Office system without enum mapping errors
    @Column(name = "WFStatus", length = 50)
    @Builder.Default
    private String wfStatus = "DRAFT";

    @Column(name = "Original_File_Path", length = 150)
    private String originalFilePath;

    @Column(name = "Selected_File_Path", length = 150)
    private String selectedFilePath;

    @Column(name = "TotalClaim", precision = 13, scale = 2)
    @Builder.Default
    private BigDecimal totalClaim = BigDecimal.ZERO;

    @Column(name = "CreatedDate")
    private LocalDateTime createdDate;

    // ── Enum for code use only — NOT mapped to DB column ─────────
    // Use getWfStatusEnum() to get typed status in Java code
    public enum WFStatus {
        DRAFT,
        SUBMITTED,
        PM_APPROVED,
        PM_REJECTED,
        SUPERIOR_APPROVED,
        SUPERIOR_REJECTED,
        FINANCE_PROCESSED,
        // Legacy values from old Liquid Office system
        PENDING, NEW, APPROVED, REJECTED, COMPLETED
    }

    public WFStatus getWfStatusEnum() {
        if (wfStatus == null) return WFStatus.DRAFT;
        try {
            return WFStatus.valueOf(wfStatus.toUpperCase().replace(" ", "_"));
        } catch (IllegalArgumentException e) {
            // Unknown legacy status - treat as DRAFT
            return WFStatus.DRAFT;
        }
    }

    public void setWfStatusEnum(WFStatus status) {
        this.wfStatus = status.name();
    }

    @PrePersist
    public void prePersist() {
        if (this.createdDate == null) this.createdDate = LocalDateTime.now();
    }
}
