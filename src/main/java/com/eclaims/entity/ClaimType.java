package com.eclaims.entity;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity @Table(name = "EN_TBL_MAST_CLAIM_TYPE")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ClaimType {
    @Id @Column(name = "ClaimID", length = 50) private String claimId;
    @Column(name = "ClaimTitle", length = 50) private String claimTitle;
    @Column(name = "ClaimDescription", length = 150) private String claimDescription;
}
