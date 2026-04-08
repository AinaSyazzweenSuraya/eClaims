package com.eclaims.entity;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity @Table(name = "EN_TBL_MAST_PROJECT_MANAGER")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ProjectManager {
    @Id @Column(name = "ProjectManagerID", length = 50) private String projectManagerId;
    @Column(name = "StaffID", length = 50) private String staffId;
    @Column(name = "ManagerName", length = 50) private String managerName;
    @Column(name = "Email", length = 255) private String email;
}
