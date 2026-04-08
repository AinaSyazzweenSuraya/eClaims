package com.eclaims.dto;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ClaimFormDto {
    private String workflowId;
    private String formId;
    private String staffId;
    private String staffName;
    private String designation;
    private String department;
    private String company;
    private String category;
    private String approverId;
    private String approverName;
    private String wfStatus;
    private BigDecimal totalClaim;
    private LocalDateTime createdDate;
    @Builder.Default private List<ClaimRowDto> rows = new ArrayList<>();
    private boolean readOnly;
    private boolean canEdit;
}
