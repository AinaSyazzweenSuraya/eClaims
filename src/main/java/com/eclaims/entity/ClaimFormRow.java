package com.eclaims.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "EN_TBL_DATA_CLAIM_FORM_TABLE")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClaimFormRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "RowID")
    private Integer rowId;

    @Column(name = "WorkflowID", length = 50)
    private String workflowId;

    @Column(name = "FormID", length = 50)
    private String formId;

    @Column(name = "Date")
    private LocalDate date;

    // CL01–CL12
    @Column(name = "ClaimID", length = 50)
    private String claimId;

    @Column(name = "Description", length = 150)
    private String description;

    @Column(name = "ProjectManagerID", length = 50)
    private String projectManagerId;

    @Column(name = "PMStatus", length = 50)
    private String pmStatus;

    // Meal fields (Time From)
    @Column(name = "TimeFrom", length = 50)
    private String timeFrom;

    // Meal fields (Time To)
    @Column(name = "TimeTo", length = 50)
    private String timeTo;

    // Medical field
    @Column(name = "MedicalClinic", length = 50)
    private String medicalClinic;

    // Party claim type
    @Column(name = "PartyType", length = 20)
    private String partyType;

    // Receipt No
    @Column(name = "ReceiptNo", length = 50)
    private String receiptNo;

    // Mileage fields
    @Column(name = "MileageVehicleType", length = 50)
    private String mileageVehicleType;

    @Column(name = "MileageKM")
    private Integer mileageKm;

    // Travel fields
    @Column(name = "TravelID", length = 150)
    private String travelId;

    @Column(name = "MealID", length = 150)
    private String mealId;

    @Column(name = "Amount", precision = 13, scale = 2)
    private BigDecimal amount;

    @Column(name = "Total", precision = 13, scale = 2)
    private BigDecimal total;

    @Column(name = "CStatus", length = 50)
    @Builder.Default
    private String cStatus = "new";

    @Column(name = "CreatedDate")
    private LocalDateTime createdDate;

    @Column(name = "AttachmentPath", length = 500)
    private String attachmentPath;

    @Column(name = "AttachmentOriginalName", length = 255)
    private String attachmentOriginalName;

    @PrePersist
    public void prePersist() {
        if (this.createdDate == null) this.createdDate = LocalDateTime.now();
    }
}
