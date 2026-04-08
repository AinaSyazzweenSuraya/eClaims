package com.eclaims.entity;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity @Table(name = "EN_TBL_MAST_TRAVEL")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class TravelLocation {
    @Id @Column(name = "TravelID", length = 50) private String travelId;
    @Column(name = "Location", length = 150) private String location;
    @Column(name = "ClaimLimit", precision = 10, scale = 2) private BigDecimal claimLimit;
}
