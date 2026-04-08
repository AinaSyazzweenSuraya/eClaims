package com.eclaims.entity;
import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name = "EN_TBL_MAST_COMPANY")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Company {
    @Id @Column(name = "CompanyID", length = 50) private String companyId;
    @Column(name = "CompanyName", length = 100) private String companyName;
}
