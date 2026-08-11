package com.eclaims.dto;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ClaimRowDto {
    private Integer rowId;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;
    private String claimId;
    private String claimTitle;
    private String description;
    private String receiptNo;
    private String projectManagerId;
    private String timeFrom;
    private String timeTo;
    private String medicalClinic;
    private String mileageVehicleType;
    private Integer mileageKm;
    private String travelId;
    private String mealId;
    private BigDecimal amount;
    private BigDecimal total;
    private String attachmentPath;
    private String attachmentOriginalName;
    private boolean hasAttachment;
}
