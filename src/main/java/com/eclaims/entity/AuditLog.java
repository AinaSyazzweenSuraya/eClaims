package com.eclaims.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name = "EN_TBL_DATA_AUDITLOG")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class AuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "AuditID") private Integer auditId;
    @Column(name = "WorkflowID", length = 50) private String workflowId;
    @Column(name = "FormID", length = 50) private String formId;
    @Column(name = "StaffID", length = 50) private String staffId;
    @Column(name = "ProjectManagerID", length = 50) private String projectManagerId;
    @Column(name = "ApproverID", length = 50) private String approverId;
    @Column(name = "Finance", length = 50) private String finance;
    @Column(name = "Status", length = 50) private String status;
    @Column(name = "Remarks", length = 150) private String remarks;
    @Column(name = "CreatedDate") private LocalDateTime createdDate;
    @PrePersist public void prePersist() { if (this.createdDate == null) this.createdDate = LocalDateTime.now(); }
}
