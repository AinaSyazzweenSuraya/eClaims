package com.eclaims.entity;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity @Table(name = "EN_TBL_MAST_PANEL_MEDICAL")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class PanelMedical {
    @Id @Column(name = "PanelMedicalID", length = 50) private String panelMedicalId;
    @Column(name = "MedicalClinic", length = 50) private String medicalClinic;
    @Column(name = "IsActive") @Builder.Default private Boolean isActive = true;
}
