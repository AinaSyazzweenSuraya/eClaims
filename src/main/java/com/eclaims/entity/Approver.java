package com.eclaims.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "EN_TBL_MAST_APPROVER")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Approver {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ApproverRowID")
    private Integer approverRowId;

    @Column(name = "StaffID", length = 50)
    private String staffId;

    @Column(name = "ApproversName", length = 50)
    private String approversName;
}
