package com.eclaims.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name = "EN_TBL_DATA_CLAIM_ATTACHMENT")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ClaimAttachment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "AttachID") private Integer attachId;
    @Column(name = "WorkflowID", length = 50) private String workflowId;
    @Column(name = "FormID", length = 50) private String formId;
    @Column(name = "Attachment", length = 255) private String attachment;
    @Column(name = "StoredFileName", length = 500) private String storedFileName;
    @Column(name = "ContentType", length = 100) private String contentType;
    @Column(name = "Status", length = 50) @Builder.Default private String status = "New";
    @Column(name = "DOC_DATE") private LocalDateTime docDate;
    @PrePersist public void prePersist() { if (this.docDate == null) this.docDate = LocalDateTime.now(); }
}
