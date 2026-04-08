package com.eclaims.entity;
import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name = "EN_TBL_MAST_DEPARTMENT")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Department {
    @Id @Column(name = "DepartmentID", length = 50) private String departmentId;
    @Column(name = "DepartmentName", length = 100) private String departmentName;
}
