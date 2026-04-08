package com.eclaims.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "EN_TBL_MAST_STAFF_INFO")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StaffInfo {

    @Id
    @Column(name = "StaffID", length = 50)
    private String staffId;

    @Column(name = "Name", length = 50)
    private String name;

    @Column(name = "Designation", length = 50)
    private String designation;

    @Column(name = "Gender", length = 50)
    private String gender;

    @Column(name = "DepartmentID", length = 50)
    private String departmentId;

    @Column(name = "CompanyID", length = 50)
    private String companyId;

    @Column(name = "Email", length = 50)
    private String email;

    @Column(name = "CategoryID", length = 50)
    private String categoryId;

    @Column(name = "DateJoined")
    private LocalDate dateJoined;

    @Column(name = "DateCreated")
    private LocalDateTime dateCreated;

    @Column(name = "DateModified")
    private LocalDateTime dateModified;

    @Column(name = "ApproverID", length = 50)
    private String approverId;

    // ── Transient helpers ──
    @Transient
    private String departmentName;

    @Transient
    private String companyName;
}
